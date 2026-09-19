package by.gusto.invoice.service;

import by.gusto.common.pdf.BrandPdfSupport;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.entity.InvoiceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Рендер счёта в PDF (S25): XHTML-шаблон в фирменном стиле (бордо #7C2D24,
 * сливки #F5EDDE, графит #26201C; Russo One / Oswald / Rubik — бренд-бук 1.7).
 * Шрифты — в classpath fonts/, встраиваются (IDENTITY_H), см. BrandPdfSupport.
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfRenderer {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BrandPdfSupport brand;

    public byte[] render(InvoiceEntity invoice, List<InvoiceItem> items) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            brand.registerFonts(renderer);
            renderer.setDocumentFromString(xhtml(invoice, items));
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать PDF счёта: " + e.getMessage(), e);
        }
    }

    private String xhtml(InvoiceEntity invoice, List<InvoiceItem> items) {
        StringBuilder rows = new StringBuilder();
        for (InvoiceItem item : items) {
            rows.append("<tr>")
                    .append("<td>").append(brand.esc(brand.text(item.getProductSnapshot(), "name"))).append("</td>")
                    .append("<td class=\"num\">").append(brand.esc(brand.text(item.getProductSnapshot(), "sku"))).append("</td>")
                    .append("<td class=\"num\">").append(item.getQuantity().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(brand.esc(brand.text(item.getProductSnapshot(), "unit"))).append("</td>")
                    .append("<td class=\"num\">").append(item.getUnitPrice().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(item.getVatRate().stripTrailingZeros().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(item.getTotal().toPlainString()).append("</td>")
                    .append("</tr>");
        }

        // токены вместо String.formatted: в CSS есть «%», которое форматтер портит
        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
                <style>
                @page { size: A4; margin: 1.6cm 1.4cm; }
                body { font-family: Rubik; font-size: 9.5pt; color: #26201C; }
                .doc-number { font-family: 'Russo One'; font-size: 16pt; color: #7C2D24;
                              letter-spacing: 0.5pt; }
                .ribbon { background-color: #7C2D24; color: #F5EDDE; font-family: Oswald;
                          font-size: 10pt; letter-spacing: 2pt; padding: 4pt 8pt; margin-bottom: 12pt; }
                .parties { width: 100%; margin-top: 10pt; border-collapse: collapse; }
                .parties td { vertical-align: top; width: 50%; }
                .party-title { font-family: Oswald; font-size: 9pt; letter-spacing: 1pt;
                               color: #7C2D24; text-transform: uppercase; margin-bottom: 3pt; }
                .party-box { background-color: #F5EDDE; padding: 7pt; }
                table.items { width: 100%; border-collapse: collapse; margin-top: 14pt;
                              font-size: 9pt; }
                table.items th { font-family: Oswald; font-weight: normal; font-size: 9pt;
                                 letter-spacing: 0.8pt; color: #F5EDDE; background-color: #7C2D24;
                                 padding: 5pt 4pt; text-align: left; }
                table.items td { padding: 4.5pt 4pt; border-bottom: 0.6pt solid #C9A875; }
                td.num, th.num { text-align: right; }
                .totals { width: 45%; margin-top: 10pt; margin-left: 55%; border-collapse: collapse; }
                .totals td { padding: 3pt 4pt; }
                .totals .grand { font-family: 'Russo One'; font-size: 12pt; color: #7C2D24; }
                .note { margin-top: 10pt; color: #6b625c; font-size: 8.5pt; }
                .sign { margin-top: 36pt; width: 100%; border-collapse: collapse; }
                .sign td { width: 50%; font-size: 9pt; color: #26201C; }
                .line { border-bottom: 0.8pt solid #26201C; margin: 0 24pt; }
                .stamp { font-family: Oswald; font-size: 8pt; letter-spacing: 1.5pt;
                         color: #C9A875; text-transform: uppercase; }
                </style>
                </head>
                <body>
                  <div class="ribbon">ГУСТО &#183; СЧЁТ НА ОПЛАТУ</div>
                  <div class="doc-number">@@NUMBER@@</div>
                  <table class="parties">
                    <tr>
                      <td>
                        <div class="party-title">Поставщик</div>
                        <div class="party-box">@@SELLER@@</div>
                      </td>
                      <td>
                        <div class="party-title">Покупатель</div>
                        <div class="party-box">@@BUYER@@</div>
                      </td>
                    </tr>
                  </table>
                  <table class="items">
                    <tr>
                      <th>Товар</th><th class="num">Артикул</th><th class="num">Кол-во</th>
                      <th class="num">Ед.</th><th class="num">Цена, BYN</th>
                      <th class="num">НДС, %</th><th class="num">Сумма, BYN</th>
                    </tr>
                    @@ROWS@@
                  </table>
                  <table class="totals">
                    <tr><td>Итого:</td><td class="num grand">@@TOTAL@@ BYN</td></tr>
                    <tr><td>в т.ч. НДС (расчётно):</td><td class="num">@@VAT@@ BYN</td></tr>
                  </table>
                  <div class="note">Цены указаны с учётом НДС. Счёт действителен на момент выставления.</div>
                  <table class="sign">
                    <tr>
                      <td><div class="stamp">М.П.</div><div class="line">&#160;</div>Подпись поставщика</td>
                      <td><div class="stamp">&#160;</div><div class="line">&#160;</div>Подпись покупателя</td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("@@NUMBER@@", brand.esc(invoice.getNumber() + " от " + invoice.getIssueDate().format(RU_DATE)))
                .replace("@@SELLER@@", brand.requisites(invoice.getSellerSnapshot(),
                        new String[]{"name", "unp", "address", "bank_account", "bank_name", "bank_bic"},
                        new String[]{null, "УНП", null, "р/с", "банк", "БИК"}))
                .replace("@@BUYER@@", brand.requisites(invoice.getBuyerSnapshot(),
                        new String[]{"name", "unp", "address", "bank_account", "bank_name", "bank_bic"},
                        new String[]{null, "УНП", null, "р/с", "банк", "БИК"}))
                .replace("@@ROWS@@", rows)
                .replace("@@TOTAL@@", invoice.getTotalAmount().toPlainString())
                .replace("@@VAT@@", invoice.getTotalVat().toPlainString());
    }
}
