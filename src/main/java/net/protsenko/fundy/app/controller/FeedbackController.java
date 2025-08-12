package net.protsenko.fundy.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.utils.FeedbackTelegramSender;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackTelegramSender telegram;

    private static String clientIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        h = req.getHeader("X-Real-IP");
        if (h != null && !h.isBlank()) return h.trim();
        return req.getRemoteAddr();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private static String esc(String s) {
        if (s == null) return "-";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String buildHtml(FeedbackRq rq, String ip, String ua) {
        return """
                <b>📬 Обратная связь</b>
                <b>Тип:</b> %s   <b>Важность:</b> %s
                <b>Email:</b> %s
                
                <b>Сообщение:</b>
                <code>%s</code>
                
                <b>IP/UA:</b> %s / %s
                """.formatted(
                safe(rq.type()), safe(rq.severity()),
                safe(rq.email()),
                esc(rq.message()),
                safe(ip), safe(ua)
        );
    }

    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody FeedbackRq rq, HttpServletRequest req) {
        String ip = clientIp(req);
        String ua = req.getHeader("User-Agent");
        telegram.send(buildHtml(rq, ip, ua));
        return ResponseEntity.ok().build();
    }

    public record FeedbackRq(
            String type,
            String severity,
            @NotBlank @Size(min = 5, max = 4000) String message,
            String email
    ) {
    }
}
