package by.gusto.inventory.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.dto.SupplierDtos.Request;
import by.gusto.inventory.dto.SupplierDtos.Response;
import by.gusto.inventory.entity.Supplier;
import by.gusto.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional
    public Response create(Request request) {
        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .unp(request.getUnp())
                .phone(request.getPhone())
                .email(request.getEmail())
                .contactPerson(request.getContactPerson())
                .note(request.getNote())
                .active(request.getActive() == null || request.getActive())
                .build();
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public Page<Response> search(String search, Boolean active, int page, int size) {
        return supplierRepository
                .search(blankToNull(search), active, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toResponse);
    }

    @Transactional
    public Response update(UUID id, Request request) {
        Supplier supplier = find(id);
        supplier.setName(request.getName());
        supplier.setUnp(request.getUnp());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setNote(request.getNote());
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }
        return toResponse(supplierRepository.save(supplier));
    }

    /** Деактивация вместо удаления: на поставщика ссылается история заказов. */
    @Transactional
    public void deactivate(UUID id) {
        Supplier supplier = find(id);
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    private Supplier find(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Поставщик не найден"));
    }

    private Response toResponse(Supplier supplier) {
        Response response = new Response();
        response.setId(supplier.getId());
        response.setName(supplier.getName());
        response.setUnp(supplier.getUnp());
        response.setPhone(supplier.getPhone());
        response.setEmail(supplier.getEmail());
        response.setContactPerson(supplier.getContactPerson());
        response.setNote(supplier.getNote());
        response.setActive(supplier.isActive());
        response.setCreatedAt(supplier.getCreatedAt());
        return response;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
