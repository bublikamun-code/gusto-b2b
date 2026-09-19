package by.gusto.common.pdf;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Общая поддержка бренд-PDF (1.7): регистрация шрифтов Russo One / Oswald / Rubik
 * с встраиванием кириллицы (IDENTITY_H) и экранирование значений.
 */
@Component
public class BrandPdfSupport {

    private static final String[][] FONTS = {
            {"fonts/RussoOne-Regular.ttf", "Russo One"},
            {"fonts/Oswald-Regular.ttf", "Oswald"},
            {"fonts/Rubik-Regular.ttf", "Rubik"},
    };

    public void registerFonts(org.xhtmlrenderer.pdf.ITextRenderer renderer) throws Exception {
        for (String[] font : FONTS) {
            renderer.getFontResolver()
                    .addFont(tempCopy(font[0]), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
    }

    public String esc(String value) {
        return value == null ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public String text(Map<String, Object> snapshot, String key) {
        if (snapshot == null) {
            return "";
        }
        Object value = snapshot.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public String requisites(Map<String, Object> map, String[] keys, String[] labels) {
        if (map == null) {
            return "";
        }
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

    /** Flying Saucer читает шрифты с файловой системы — извлекаем из classpath. */
    private String tempCopy(String classpath) throws Exception {
        Path tmp = Files.createTempFile("gusto-font-", ".ttf");
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp.toAbsolutePath().toString();
    }
}
