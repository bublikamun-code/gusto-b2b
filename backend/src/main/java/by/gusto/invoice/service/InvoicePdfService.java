package by.gusto.invoice.service;

import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.service.FileStorageService;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.entity.InvoiceItem;
import by.gusto.invoice.repository.InvoiceItemRepository;
import by.gusto.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * PDF счёта (S25): рендер в фирменном стиле и хранение в files как PRIVATE (1.6) —
 * документ доступен только по правам на счёт, через стриминг backend.
 * PDF генерируется лениво и кэшируется (invoices.pdf_file_id).
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final InvoicePdfRenderer renderer;

    @Transactional
    public FileEntity ensurePdf(UUID invoiceId, UUID ownerId) {
        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));

        if (invoice.getPdfFileId() != null) {
            return fileRepository.findById(invoice.getPdfFileId())
                    .orElseThrow(() -> new IllegalStateException("PDF-файл счёта утерян"));
        }

        List<InvoiceItem> items = invoiceItemRepository.findAllByInvoiceId(invoiceId);
        byte[] pdf = renderer.render(invoice, items);

        String storageKey = UUID.randomUUID().toString();
        fileStorageService.store(new ByteArrayInputStream(pdf), storageKey);

        FileEntity file = fileRepository.save(FileEntity.builder()
                .storageKey(storageKey)
                .originalName(invoice.getNumber() + ".pdf")
                .mimeType("application/pdf")
                .sizeBytes((long) pdf.length)
                .ownerId(ownerId)
                .visibility(FileEntity.Visibility.PRIVATE)
                .build());

        invoice.setPdfFileId(file.getId());
        invoiceRepository.save(invoice);
        return file;
    }

    @Transactional(readOnly = true)
    public InputStream load(FileEntity file) {
        return fileStorageService.load(file.getStorageKey());
    }
}
