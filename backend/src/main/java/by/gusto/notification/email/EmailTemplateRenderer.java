package by.gusto.notification.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Бренд-обёртка писем (S33): сливки #F5EDDE / бордо #7C2D24 / графит #26201C,
 * шрифты — системные стеки (в письмах нельзя полагаться на веб-шрифты).
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    public String render(String title, String bodyHtml, String buttonUrl, String buttonText) {
        String button = buttonUrl == null ? "" : """
                <tr><td style="padding:8px 24px 4px;">
                  <a href="%s" style="display:inline-block;background-color:#7C2D24;color:#F5EDDE;
                     font-family:Arial,Helvetica,sans-serif;font-size:14px;padding:10px 22px;
                     border-radius:4px;text-decoration:none;">%s</a>
                </td></tr>
                """.formatted(buttonUrl, buttonText == null ? "Открыть" : buttonText);
        return """
                <html><body style="margin:0;padding:0;background-color:#F5EDDE;">
                  <div style="max-width:560px;margin:0 auto;background-color:#F5EDDE;">
                    <div style="background-color:#7C2D24;padding:14px 24px;">
                      <span style="color:#F5EDDE;font-family:Arial,Helvetica,sans-serif;
                            font-size:18px;letter-spacing:3px;">ГУСТО</span>
                    </div>
                    <table style="width:100%%;border-collapse:collapse;">
                      <tr><td style="padding:20px 24px 6px;color:#26201C;
                            font-family:Arial,Helvetica,sans-serif;font-size:19px;font-weight:bold;">
                        %s
                      </td></tr>
                      <tr><td style="padding:4px 24px 16px;color:#26201C;
                            font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.6;">
                        %s
                      </td></tr>
                      %s
                      <tr><td style="padding:10px 24px 24px;color:#8a8078;
                            font-family:Arial,Helvetica,sans-serif;font-size:11px;">
                        ГУСТО — мясной гастроном. Это автоматическое письмо.
                      </td></tr>
                    </table>
                  </div>
                </body></html>
                """.formatted(title, bodyHtml, button);
    }
}
