package net.protsenko.fundy.app.utils;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class FeedbackTelegramSender {

    private final RestTemplate rt;
    @Value("${fundy.feedback.telegram.token:}")
    private String token;
    @Value("${fundy.feedback.telegram.chat-id:}")
    private String chatId;

    public FeedbackTelegramSender(RestTemplateBuilder b) {
        this.rt = b.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void send(String html) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            log.info("FEEDBACK:\n{}", html.replaceAll("<[^>]+>", ""));
            return;
        }
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", html,
                "parse_mode", "HTML",
                "disable_web_page_preview", true
        );
        try {
            rt.postForEntity(url,
                    org.springframework.http.RequestEntity
                            .post(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body),
                    Void.class);
        } catch (Exception e) {
            log.warn("Telegram send failed", e);
        }
    }
}
