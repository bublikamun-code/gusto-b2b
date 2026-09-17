package by.gusto.inventory;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.entity.StockMovement;
import by.gusto.inventory.repository.StockBalanceRepository;
import by.gusto.inventory.repository.StockMovementRepository;
import by.gusto.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S18: остатки, движения, резерв. Демо-данные — из V6 (товары) и V9 (склад и остатки).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StockIntegrationTest {

    private static final UUID DEFAULT_LOCATION = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection("redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private StockService stockService;

    @Autowired
    private StockBalanceRepository balanceRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

    private StockBalance balance(String sku) {
        StockBalance.StockBalanceId id = new StockBalance.StockBalanceId(productId(sku), DEFAULT_LOCATION);
        return balanceRepository.findById(id).orElseThrow();
    }

    @Test
    void defaultLocationAndSeedBalancesApplied() {
        assertThat(stockService.defaultLocationId()).isEqualTo(DEFAULT_LOCATION);
        assertThat(stockService.availableByProduct(DEFAULT_LOCATION, List.of(productId("steyk-ribay"))))
                .containsEntry(productId("steyk-ribay"), new BigDecimal("40.000"));
        // начальный приход отражён и в журнале
        List<StockMovement> movements = movementRepository
                .findAllByProductIdAndLocationIdOrderByCreatedAtDesc(productId("steyk-ribay"), DEFAULT_LOCATION);
        assertThat(movements).anyMatch(m -> m.getType() == StockMovement.Type.INCOMING
                && m.getQuantity().compareTo(new BigDecimal("40.000")) == 0);
    }

    @Test
    void movementsChangeBalanceAndWriteJournal() {
        UUID product = productId("steyk-na-kosti"); // seed 25

        stockService.applyMovement(product, DEFAULT_LOCATION, StockMovement.Type.INCOMING,
                new BigDecimal("100.000"), "TEST", null, "приход", null);
        assertThat(balance("steyk-na-kosti").getQuantity()).isEqualByComparingTo("125.000");

        stockService.applyMovement(product, DEFAULT_LOCATION, StockMovement.Type.OUTGOING,
                new BigDecimal("30.000"), "TEST", null, "расход", null);
        StockBalance result = balance("steyk-na-kosti");
        assertThat(result.getQuantity()).isEqualByComparingTo("95.000");
        assertThat(result.getReserved()).isEqualByComparingTo("0");
        assertThat(movementRepository.findAllByProductIdAndLocationIdOrderByCreatedAtDesc(product, DEFAULT_LOCATION))
                .anyMatch(m -> m.getType() == StockMovement.Type.OUTGOING
                        && m.getQuantity().compareTo(new BigDecimal("-30.000")) == 0);
    }

    @Test
    void outgoingCannotDriveAvailableNegative() {
        UUID product = productId("vyrezka-svinaya"); // seed 30
        GustoException e = assertThrows(GustoException.class, () ->
                stockService.applyMovement(product, DEFAULT_LOCATION, StockMovement.Type.OUTGOING,
                        new BigDecimal("31.000"), "TEST", null, null, null));
        assertThat(e.getErrorCode().getCode()).isEqualTo("STOCK_INSUFFICIENT");
        assertThat(balance("vyrezka-svinaya").getQuantity()).isEqualByComparingTo("30.000");
    }

    @Test
    void reserveAndReleaseChangeOnlyReserved() {
        UUID product = productId("bedro-kurinoye"); // seed 60

        stockService.reserve(product, DEFAULT_LOCATION, new BigDecimal("25.000"), "ORDER", UUID.randomUUID(), null);
        StockBalance reserved = balance("bedro-kurinoye");
        assertThat(reserved.getQuantity()).isEqualByComparingTo("60.000");
        assertThat(reserved.getReserved()).isEqualByComparingTo("25.000");

        stockService.release(product, DEFAULT_LOCATION, new BigDecimal("10.000"), "ORDER", UUID.randomUUID(), null);
        StockBalance released = balance("bedro-kurinoye");
        assertThat(released.getQuantity()).isEqualByComparingTo("60.000");
        assertThat(released.getReserved()).isEqualByComparingTo("15.000");
    }

    @Test
    void reserveBeyondAvailableRejected() {
        UUID product = productId("kolbaski-dlya-zharki"); // seed 35
        GustoException e = assertThrows(GustoException.class, () ->
                stockService.reserve(product, DEFAULT_LOCATION, new BigDecimal("35.001"), "ORDER", UUID.randomUUID(), null));
        assertThat(e.getErrorCode().getCode()).isEqualTo("STOCK_INSUFFICIENT");
        assertThat(balance("kolbaski-dlya-zharki").getReserved()).isEqualByComparingTo("0");
    }

    @Test
    void concurrentReservationsCannotOversell() throws Exception {
        UUID product = productId("yaytsa-kurinye-s0"); // seed 120
        int threads = 8;
        BigDecimal each = new BigDecimal("20.000"); // 8 × 20 = 160 > 120, поместится ровно 6

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Callable<Boolean> task = () -> {
                try {
                    stockService.reserve(product, DEFAULT_LOCATION, each, "ORDER", UUID.randomUUID(), null);
                    return true;
                } catch (GustoException e) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            int successes = 0;
            for (Future<Boolean> future : futures) {
                successes += future.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(successes).isEqualTo(6);
            StockBalance finalBalance = balance("yaytsa-kurinye-s0");
            assertThat(finalBalance.getReserved()).isEqualByComparingTo("120.000");
            assertThat(finalBalance.available()).isEqualByComparingTo("0.000");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCatalogExposesStockStatus() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        org.springframework.http.ResponseEntity<ApiResponse> response =
                restTemplate.getForEntity("/api/v1/catalog/products?size=50", ApiResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().getData();
        assertThat(items).isNotEmpty();

        Map<String, Map<String, Object>> bySku = items.stream()
                .collect(java.util.stream.Collectors.toMap(i -> (String) i.get("sku"), i -> i));
        assertThat(bySku.get("steyk-ribay").get("stockStatus")).isEqualTo("IN_STOCK");
        assertThat(bySku.get("farsh-kuriniy").get("stockStatus")).isEqualTo("PREORDER");
    }
}
