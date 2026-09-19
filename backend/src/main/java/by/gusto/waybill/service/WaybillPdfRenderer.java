package by.gusto.waybill.service;

import by.gusto.common.pdf.BrandPdfSupport;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.repository.InvoiceRepository;
import by.gusto.waybill.entity.WaybillEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Рендер ТН/ТТН в PDF (S26): структура по примеру ТиоптТрейд —
 * грузоотправитель / грузополучатель / заказчик, авто и водитель (ТТН),
 * позиции с массой, НДС расчётно, места под подписи и печать.
 */
@Service
@RequiredArgsConstructor
public class WaybillPdfRenderer {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BrandPdfSupport brand;

    public byte[] render(WaybillEntity waybill, List<by.gusto.waybill.entity.WaybillItem> items) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            brand.registerFonts(renderer);
            renderer.setDocumentFromString(xhtml(waybill, items));
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать PDF накладной: " + e.getMessage(), e);
        }
    }

    private String xhtml(WaybillEntity waybill, List<by.gusto.waybill.entity.WaybillItem> items) {
        String typeTitle = waybill.getType() == WaybillEntity.Type.TTN
                ? "ТОВАРНО-ТРАНСПОРТНАЯ НАКЛАДНАЯ"
                : "ТОВАРНАЯ НАКЛАДНАЯ";

        StringBuilder rows = new StringBuilder();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        for (var item : items) {
            rows.append("<tr>")
                    .append("<td>").append(brand.esc(brand.text(item.getProductSnapshot(), "name"))).append("</td>")
                    .append("<td class=\"num\">").append(brand.esc(brand.text(item.getProductSnapshot(), "sku"))).append("</td>")
                    .append("<td class=\"num\">").append(item.getQuantity().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(brand.esc(brand.text(item.getProductSnapshot(), "unit"))).append("</td>")
                    .append("<td class=\"num\">").append(item.getUnitPrice().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(item.getTotal().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(item.getWeight() == null ? "&#8212;" : item.getWeight().toPlainString()).append("</td>")
                    .append("</tr>");
            if (item.getWeight() != null) {
                totalWeight = totalWeight.add(item.getWeight());
            }
            totalAmount = totalAmount.add(item.getTotal());
            totalVat = totalVat.add(item.getTotal().multiply(item.getVatRate())
                    .divide(BigDecimal.valueOf(100).add(item.getVatRate()), 2, RoundingMode.HALF_UP));
        }

        Map consignee = consigneeMap(waybill);
        String consigneeBlock = consignee == null ? "" : """
                <tr>
                  <td>
                    <div class="party-title">Грузополучатель</div>
                    <div class="party-box">%s</div>
                  </td>
                  <td></td>
                </tr>
                """.formatted(brand.requisites(consignee,
                new String[]{"name", "unp", "address"},
                new String[]{null, "УНП", null}));

        String transportBlock = waybill.getType() == WaybillEntity.Type.TTN ? """
                <table class="transport">
                  <tr>
                    <td><b>Автомобиль:</b> %s</td>
                    <td><b>Водитель:</b> %s</td>
                  </tr>
                  <tr>
                    <td><b>Организация-перевозчик:</b> %s</td>
                    <td></td>
                  </tr>
                </table>
                """.formatted(
                brand.esc(value(waybill.getCarrierSnapshot(), "vehicle", "&#8212;")),
                brand.esc(value(waybill.getCarrierSnapshot(), "driver", "&#8212;")),
                brand.esc(value(waybill.getCarrierSnapshot(), "carrierCompany", "&#8212;"))) : "";

        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
                <style>
                @page { size: A4; margin: 1.4cm 1.2cm; }
                body { font-family: Rubik; font-size: 9.5pt; color: #26201C; }
                .ribbon { background-color: #7C2D24; color: #F5EDDE; font-family: Oswald;
                          font-size: 10pt; letter-spacing: 2pt; padding: 4pt 8pt; }
                .doc-number { font-family: 'Russo One'; font-size: 14pt; color: #7C2D24; margin-top: 8pt; }
                .doc-title { font-family: Oswald; font-size: 11pt; letter-spacing: 1.5pt; margin-top: 2pt; }
                .parties { width: 100%; margin-top: 10pt; border-collapse: collapse; }
                .parties td { vertical-align: top; width: 50%; }
                .party-title { font-family: Oswald; font-size: 9pt; letter-spacing: 1pt;
                               color: #7C2D24; text-transform: uppercase; margin-bottom: 3pt; }
                .party-box { background-color: #F5EDDE; padding: 6pt; }
                table.transport { width: 100%; margin-top: 8pt; border-collapse: collapse;
                                  font-size: 9pt; }
                table.transport td { padding: 2.5pt 4pt; }
                table.items { width: 100%; border-collapse: collapse; margin-top: 10pt; font-size: 9pt; }
                table.items th { font-family: Oswald; font-weight: normal; font-size: 9pt;
                                 letter-spacing: 0.6pt; color: #F5EDDE; background-color: #7C2D24;
                                 padding: 5pt 4pt; text-align: left; }
                table.items td { padding: 4pt; border-bottom: 0.6pt solid #C9A875; }
                td.num, th.num { text-align: right; }
                .totals { width: 50%; margin-top: 10pt; margin-left: 50%; border-collapse: collapse; }
                .totals td { padding: 3pt 4pt; }
                .totals .grand { font-family: 'Russo One'; font-size: 11pt; color: #7C2D24; }
                .sign { margin-top: 32pt; width: 100%; border-collapse: collapse; }
                .sign td { width: 50%; font-size: 9pt; }
                .line { border-bottom: 0.8pt solid #26201C; margin: 0 24pt; }
                .stamp { font-family: Oswald; font-size: 8pt; letter-spacing: 1.5pt;
                         color: #C9A875; text-transform: uppercase; }
                </style>
                </head>
                <body>
                  <div class="ribbon">ГУСТО</div>
                  <div class="doc-number">@@NUMBER@@</div>
                  <div class="doc-title">@@TITLE@@</div>
                  <table class="parties">
                    <tr>
                      <td>
                        <div class="party-title">Грузоотправитель</div>
                        <div class="party-box">@@SELLER@@</div>
                      </td>
                      <td>
                        <div class="party-title">Заказчик (плательщик)</div>
                        <div class="party-box">@@BUYER@@</div>
                      </td>
                    </tr>
                    @@CONSIGNEE@@
                  </table>
                  @@TRANSPORT@@
                  <table class="items">
                    <tr>
                      <th>Товар</th><th class="num">Артикул</th><th class="num">Кол-во</th>
                      <th class="num">Ед.</th><th class="num">Цена, BYN</th>
                      <th class="num">Сумма, BYN</th><th class="num">Масса, кг</th>
                    </tr>
                    @@ROWS@@
                  </table>
                  <table class="totals">
                    <tr><td>Всего на сумму:</td><td class="num grand">@@TOTAL@@ BYN</td></tr>
                    <tr><td>в т.ч. НДС (расчётно):</td><td class="num">@@VAT@@ BYN</td></tr>
                    <tr><td>Масса груза:</td><td class="num">@@WEIGHT@@ кг</td></tr>
                  </table>
                  <table class="sign">
                    <tr>
                      <td><div class="stamp">М.П.</div><div class="line">&#160;</div>Отпустил (подпись)</td>
                      <td><div class="line">&#160;</div>Принял (подпись)</td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("@@NUMBER@@", brand.esc(waybill.getNumber() + " от " + waybill.getIssueDate().format(RU_DATE)))
                .replace("@@TITLE@@", typeTitle)
                .replace("@@SELLER@@", brand.requisites(waybill.getSellerSnapshot(),
                        new String[]{"name", "unp", "address"},
                        new String[]{null, "УНП", null}))
                .replace("@@BUYER@@", brand.requisites(waybill.getBuyerSnapshot(),
                        new String[]{"name", "unp", "address"},
                        new String[]{null, "УНП", null}))
                .replace("@@CONSIGNEE@@", consigneeBlock)
                .replace("@@TRANSPORT@@", transportBlock)
                .replace("@@ROWS@@", rows)
                .replace("@@TOTAL@@", totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .replace("@@VAT@@", totalVat.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .replace("@@WEIGHT@@", totalWeight.setScale(3, RoundingMode.HALF_UP).toPlainString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> consigneeMap(WaybillEntity waybill) {
        Object consignee = waybill.getBuyerSnapshot() == null ? null
                : waybill.getBuyerSnapshot().get("consignee");
        return consignee instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String value(Map<String, Object> map, String key, String fallback) {
        if (map == null || map.get(key) == null || String.valueOf(map.get(key)).isBlank()) {
            return fallback;
        }
        return String.valueOf(map.get(key));
    }
}
