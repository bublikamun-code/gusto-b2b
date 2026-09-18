package by.gusto.invoice.service;

import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.entity.InvoiceItem;
import com.lowagie.text.pdf.BaseFont;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Рендер счёта в PDF (S25): XHTML-шаблон в фирменном стиле (бордо #7C2D24,
 * сливки #F5EDDE, графит #26201C; Russo One / Oswald / Rubik — бренд-бук 1.7).
 * Кириллические TTF лежат в classpath (fonts/) и встраиваются в документ
 * (BaseFont.IDENTITY_H); шрифты OFL, кириллица проверена при подключении.
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfRenderer {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String[][] FONTS = {
            {"fonts/RussoOne-Regular.ttf", "Russo One"},
            {"fonts/Oswald-Regular.ttf", "Oswald"},
            {"fonts/Rubik-Regular.ttf", "Rubik"},
    };

    public byte[] render(InvoiceEntity invoice, List<InvoiceItem> items) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            for (String[] font : FONTS) {
                renderer.getFontResolver().addFont(tempCopy(font[0]), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
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
                    .append("<td>").append(esc(text(item, "name"))).append("</td>")
                    .append("<td class=\"num\">").append(esc(text(item, "sku"))).append("</td>")
                    .append("<td class=\"num\">").append(plain(item.getQuantity())).append("</td>")
                    .append("<td class=\"num\">").append(esc(text(item, "unit"))).append("</td>")
                    .append("<td class=\"num\">").append(plain(item.getUnitPrice())).append("</td>")
                    .append("<td class=\"num\">").append(item.getVatRate().stripTrailingZeros().toPlainString()).append("</td>")
                    .append("<td class=\"num\">").append(plain(item.getTotal())).append("</td>")
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
                .replace("@@NUMBER@@", esc(invoice.getNumber() + " от " + invoice.getIssueDate().format(RU_DATE)))
                .replace("@@SELLER@@", requisites(invoice.getSellerSnapshot()))
                .replace("@@BUYER@@", requisites(invoice.getBuyerSnapshot()))
                .replace("@@ROWS@@", rows)
                .replace("@@TOTAL@@", plain(invoice.getTotalAmount()))
                .replace("@@VAT@@", plain(invoice.getTotalVat()));
    }

    private String requisites(java.util.Map<String, Object> map) {
        if (map == null) {
            return "";
        }
        String[] keys = {"name", "unp", "address", "bank_account", "bank_name", "bank_bic"};
        String[] labels = {null, "УНП", null, "р/с", "банк", "БИК"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            Object value = map.get(keys[i]);
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("<br/>");
            }
            sb.append(labels[i] == null ? "" : labels[i] + " ")
                    .append(esc(String.valueOf(value)));
        }
        return sb.toString();
    }

    private String text(InvoiceItem item, String key) {
        Object value = item.getProductSnapshot().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String esc(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Flying Saucer читает шрифты с файловой системы — извлекаем из classpath во временный файл. */
    private String tempCopy(String classpath) throws Exception {
        Path tmp = Files.createTempFile("gusto-font-", ".ttf");
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp.toAbsolutePath().toString();
    }
}
