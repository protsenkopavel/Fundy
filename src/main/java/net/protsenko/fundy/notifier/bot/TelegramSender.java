package net.protsenko.fundy.notifier.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramSender {

    private final FundingBot bot;

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMessage(long chatId, String text, InlineKeyboardMarkup kb) {
        SendMessage sm = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .replyMarkup(kb)
                .build();
        try {
            bot.execute(sm);
        } catch (Exception e) {
            log.warn("TG send error", e);
        }
    }
}
