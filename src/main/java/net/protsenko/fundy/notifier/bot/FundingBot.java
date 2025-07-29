package net.protsenko.fundy.notifier.bot;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.notifier.service.FundingAggregatorService;
import net.protsenko.fundy.notifier.service.FundingSnapshotCache;
import net.protsenko.fundy.notifier.util.FundingMessageFormatter;
import org.springframework.beans.factory.annotation.Value;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import net.protsenko.fundy.notifier.repo.UserSettingsRepo;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FundingBot extends TelegramLongPollingBot {

    private final String username;
    private final String token;
    private final UserSettingsRepo repo;
    private final BotStateStore stateStore;
    private final FundingSnapshotCache cache;
    private final Executor previewExecutor;

    public FundingBot(
            @Value("${telegram.bot.username}") String username,
            @Value("${telegram.bot.token}") String token,
            UserSettingsRepo repo,
            BotStateStore stateStore,
            FundingSnapshotCache cache,
            Executor previewExecutor
    ) {
        this.username = username;
        this.token = token;
        this.repo = repo;
        this.stateStore = stateStore;
        this.cache = cache;
        this.previewExecutor = previewExecutor;
    }

    @PostConstruct
    void init() {
        log.info("FundingBot init: username={}, tokenPresent={}", username, token != null && !token.isBlank());
    }

    @PostConstruct
    void registerCommands() {
        var commands = List.of(
                new BotCommand("start", "Запустить бота"),
                new BotCommand("menu", "Открыть меню настроек"),
                new BotCommand("top", "Показать топ фандингов сейчас"),
                new BotCommand("min", "Мин. ставка (%)"),
                new BotCommand("before", "За сколько до начисления"),
                new BotCommand("tz", "Часовой пояс"),
                new BotCommand("exchanges", "Выбор бирж"),
                new BotCommand("help", "Справка"),
                new BotCommand("stop", "Остановить уведомления"),
                new BotCommand("ping", "Проверка связи")
        );
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (Exception e) {
            log.warn("SetMyCommands failed", e);
        }
    }

    @Override public String getBotUsername() { return username; }
    @Override public String getBotToken()    { return token; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleText(update);
            }
        } catch (Exception e) {
            log.error("Update handling error", e);
        }
    }

    // ---------- TEXT ----------
    private void handleText(Update upd) {
        long chatId = upd.getMessage().getChatId();
        String text = upd.getMessage().getText().trim();
        String[] parts = text.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        // Любая команда сбрасывает состояние
        if (text.startsWith("/")) {
            stateStore.set(chatId, BotState.NONE);
        }

        BotState st = stateStore.get(chatId);
        if (st != BotState.NONE && !text.startsWith("/")) {
            processWaitingState(chatId, text, st);
            return;
        }

        switch (cmd) {
            case "/start" -> {
                repo.save(repo.getOrDefault(chatId));
                send(chatId, "Привет! Я буду присылать фандинги. Нажми /menu", null);
            }
            case "/menu"     -> sendMenuNew(chatId);
            case "/top"      -> previewAsync(chatId); // отправляет ⏳ и редактирует
            case "/min"      -> handleSlashMin(chatId, args);
            case "/before"   -> handleSlashBefore(chatId, args);
            case "/tz"       -> handleSlashTz(chatId, args);
            case "/exchanges"-> showExchangeToggles(chatId, null);
            case "/help"     -> send(chatId, helpText(), null);
            case "/stop"     -> send(chatId, "Ок, не буду слать уведомления. (/start чтобы включить)", null);
            case "/ping"     -> send(chatId, "pong", null);
            default          -> send(chatId, "Команда не распознана. Попробуй /help", null);
        }
    }

    private void handleSlashMin(long chatId, String args) {
        if (args.isBlank()) {
            send(chatId, "Пример: /min 0.5", null);
            return;
        }
        try {
            BigDecimal p = new BigDecimal(args).divide(BigDecimal.valueOf(100));
            var old = repo.getOrDefault(chatId);
            repo.save(new FundingAlertSettings(chatId, p, old.exchanges(), old.notifyBefore(), old.zone()));
            send(chatId, "Мин. ставка: " + p.multiply(BigDecimal.valueOf(100)) + "%", null);
        } catch (Exception e) {
            send(chatId, "Не понял число. Пример: /min 0.5", null);
        }
    }

    private void handleSlashBefore(long chatId, String args) {
        if (args.isBlank()) {
            send(chatId, "Пример: /before 30m или /before 1h", null);
            return;
        }
        try {
            Duration d = parseDuration(args);
            var old = repo.getOrDefault(chatId);
            repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), old.exchanges(), d, old.zone()));
            send(chatId, "За сколько до: " + FundingMessageFormatter.prettyDuration(d), null);
        } catch (Exception e) {
            send(chatId, "Не понял интервал. Пример: /before 30m", null);
        }
    }

    private void handleSlashTz(long chatId, String args) {
        if (args.isBlank()) {
            send(chatId, "Пример: /tz Europe/Moscow", null);
            return;
        }
        try {
            ZoneId z = ZoneId.of(args);
            var old = repo.getOrDefault(chatId);
            repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), old.exchanges(), old.notifyBefore(), z));
            send(chatId, "Часовой пояс: " + z, null);
        } catch (Exception e) {
            send(chatId, "Не понял TZ. Пример: /tz Europe/Moscow", null);
        }
    }

    private String helpText() {
        return """
            Доступные команды:
            /menu — меню настроек
            /top — топ фандингов сейчас
            /min <pct> — мин. ставка, % (например /min 0.7)
            /before <30m|1h> — за сколько до начисления
            /tz <ZoneId> — часовой пояс (Europe/Moscow)
            /exchanges — выбрать биржи
            /stop — перестать слать уведомления
            """;
    }

    private InlineKeyboardMarkup previewKb() {
        return new InlineKeyboardMarkup(List.of(
                List.of(btn("📋 Меню", "BACK_MENU"), btn("🔄 Обновить", "PREVIEW"))
        ));
    }

    private void processWaitingState(long chatId, String text, BotState st) {
        FundingAlertSettings old = repo.getOrDefault(chatId);
        try {
            switch (st) {
                case WAIT_MIN -> {
                    BigDecimal p = new BigDecimal(text).divide(BigDecimal.valueOf(100));
                    repo.save(new FundingAlertSettings(chatId, p, old.exchanges(), old.notifyBefore(), old.zone()));
                    stateStore.set(chatId, BotState.NONE);
                    send(chatId, "Мин. ставка: " + p.multiply(BigDecimal.valueOf(100)) + "%", null);
                    sendMenuNew(chatId);
                }
                case WAIT_BEFORE -> {
                    Duration d = parseDuration(text);
                    repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), old.exchanges(), d, old.zone()));
                    stateStore.set(chatId, BotState.NONE);
                    send(chatId, "За сколько до: " + FundingMessageFormatter.prettyDuration(d), null);
                    sendMenuNew(chatId);
                }
                case WAIT_TZ -> {
                    ZoneId z = ZoneId.of(text);
                    repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), old.exchanges(), old.notifyBefore(), z));
                    stateStore.set(chatId, BotState.NONE);
                    send(chatId, "Часовой пояс: " + z, null);
                    sendMenuNew(chatId);
                }
            }
        } catch (Exception e) {
            send(chatId, "Не понял ввод. Попробуй ещё раз.", null);
        }
    }

    // ---------- CALLBACK ----------
    private void handleCallback(Update upd) throws Exception {
        var cb = upd.getCallbackQuery();
        long chatId = cb.getMessage().getChatId();
        Integer msgId = cb.getMessage().getMessageId();
        String data = cb.getData();

        execute(AnswerCallbackQuery.builder().callbackQueryId(cb.getId()).build());

        if ("BACK_MENU".equals(data)) {
            editMenu(chatId, msgId);
            return;
        }

        switch (data) {
            case "SET_MIN" -> {
                stateStore.set(chatId, BotState.WAIT_MIN);
                edit(chatId, msgId, "Введи минимальную ставку в %, например 0.5", null);
            }
            case "SET_EXCH" -> showExchangeToggles(chatId, msgId);
            case "SET_BEFORE" -> {
                stateStore.set(chatId, BotState.WAIT_BEFORE);
                edit(chatId, msgId, "Введи интервал до начисления, например 30m или 1h", null);
            }
            case "SET_TZ" -> {
                stateStore.set(chatId, BotState.WAIT_TZ);
                edit(chatId, msgId, "Введи tz, например Europe/Moscow", null);
            }
            case "PREVIEW" -> previewAsync(chatId); // отправим отдельное сообщение
            default -> {
                if (data.startsWith("EX_")) {
                    toggleExchange(chatId, data.substring(3));
                    showExchangeToggles(chatId, msgId);
                }
            }
        }
    }

    // ---------- PREVIEW ----------
    private void previewAsync(long chatId) {
        Message pending;
        try {
            pending = execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("⏳ Загружаю топ-фандинги…")
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(previewKb())
                    .build());
        } catch (Exception e) {
            log.warn("Cannot send pending msg", e);
            return;
        }

        int msgId = pending.getMessageId();

        previewExecutor.execute(() -> {
            try {
                String txt = buildPreviewText(chatId);
                edit(chatId, msgId, txt, previewKb());
            } catch (Exception e) {
                log.error("preview failed", e);
                edit(chatId, msgId, "Ошибка при получении данных. Попробуй позже.", previewKb());
            }
        });
    }

    private String buildPreviewText(long chatId) {
        var s = repo.getOrDefault(chatId);

        Map<ExchangeType, List<FundingRateData>> snap = cache.getLastSnapshot();
        if (snap.isEmpty() || cache.isStale()) {
            snap = cache.forceRefresh(Duration.ofSeconds(5));
        }
        if (snap.isEmpty()) {
            return "Данные обновляются, обнови через 1-2 мин.";
        }

        var list = snap.entrySet().stream()
                .filter(e -> s.exchanges().isEmpty() || s.exchanges().contains(e.getKey()))
                .flatMap(e -> e.getValue().stream().map(fr -> Map.entry(e.getKey(), fr)))
                .filter(e -> e.getValue().fundingRate().abs().compareTo(s.minAbsRate()) >= 0)
                .sorted(Comparator.comparing(
                        (Map.Entry<ExchangeType, FundingRateData> e) -> e.getValue().fundingRate().abs()
                ).reversed())
                .limit(10)
                .toList();

        ZonedDateTime upd = cache.getLastUpdated().atZone(s.zone());

        String header = "Текущие фандинги > %s%%\n\n"
                .formatted(
                        s.minAbsRate().multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                                .stripTrailingZeros().toPlainString()
                );

        if (list.isEmpty()) {
            return header + "Нет подходящих ставок.";
        }

        StringBuilder sb = new StringBuilder(header);
        for (var e : list) {
            sb.append(FundingMessageFormatter.format(e.getValue(), e.getKey(), s.zone()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    // ---------- MENU / UI ----------
    private void sendMenuNew(long chatId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);
        send(chatId, menuText(s), menuKb());
    }

    private void editMenu(long chatId, Integer msgId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);
        edit(chatId, msgId, menuText(s), menuKb());
    }

    private String menuText(FundingAlertSettings s) {
        return """
                ⚙️ Настройки:
                • Мин. ставка: %s%%
                • Биржи: %s
                • За сколько до: %s
                • Часовой пояс: %s
                """.formatted(
                s.minAbsRate().multiply(BigDecimal.valueOf(100)),
                s.exchanges().isEmpty() ? "ВСЕ" : s.exchanges(),
                FundingMessageFormatter.prettyDuration(s.notifyBefore()),
                s.zone()
        );
    }

    private InlineKeyboardMarkup menuKb() {
        return new InlineKeyboardMarkup(List.of(
                List.of(btn("📉 Мин. %", "SET_MIN")),
                List.of(btn("🏦 Биржи", "SET_EXCH")),
                List.of(btn("⏰ За сколько до", "SET_BEFORE")),
                List.of(btn("🌍 Timezone", "SET_TZ")),
                List.of(btn("👀 Топ сейчас", "PREVIEW"))
        ));
    }

    private void showExchangeToggles(long chatId, Integer msgId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);
        Set<ExchangeType> set = s.exchanges();

        List<List<InlineKeyboardButton>> rows = Arrays.stream(ExchangeType.values())
                .map(ex -> {
                    boolean on = set.isEmpty() || set.contains(ex);
                    return List.of(btn((on ? "✅ " : "❌ ") + ex.name(), "EX_" + ex.name()));
                })
                .toList();

        rows.add(List.of(btn("⬅️ Назад", "BACK_MENU")));
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(rows);

        edit(chatId, msgId, "Биржи (нажимай, чтобы переключать):", kb);
    }

    private void toggleExchange(long chatId, String exStr) {
        ExchangeType ex = ExchangeType.valueOf(exStr);
        FundingAlertSettings old = repo.getOrDefault(chatId);
        Set<ExchangeType> set = new HashSet<>(old.exchanges());
        if (set.isEmpty()) { // было "ВСЕ"
            set.addAll(Arrays.asList(ExchangeType.values()));
        }
        if (!set.add(ex)) set.remove(ex);
        repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), set, old.notifyBefore(), old.zone()));
    }

    // ---------- utils ----------
    private InlineKeyboardButton btn(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    private void send(long chatId, String text, InlineKeyboardMarkup kb) {
        try {
            execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(kb)
                    .build());
        } catch (Exception e) {
            log.warn("Send failed", e);
        }
    }

    private void edit(long chatId, Integer msgId, String text, InlineKeyboardMarkup kb) {
        try {
            EditMessageText em = new EditMessageText();
            em.setChatId(String.valueOf(chatId));
            em.setMessageId(msgId);
            em.setText(text);
            em.setParseMode("HTML");
            em.setDisableWebPagePreview(true);
            if (kb != null) em.setReplyMarkup(kb);
            execute(em);
        } catch (Exception e) {
            log.warn("Edit failed", e);
        }
    }

    private Duration parseDuration(String s) {
        s = s.toLowerCase(Locale.ROOT).trim();
        long hours = 0, minutes = 0;
        int idxH = s.indexOf('h');
        if (idxH >= 0) {
            hours = Long.parseLong(s.substring(0, idxH));
            s = s.substring(idxH + 1);
        }
        int idxM = s.indexOf('m');
        if (idxM >= 0 && !s.isBlank()) {
            minutes = Long.parseLong(s.substring(0, idxM));
        }
        if (hours == 0 && minutes == 0) {
            return Duration.parse("PT" + s.toUpperCase()); // PT30M
        }
        return Duration.ofHours(hours).plusMinutes(minutes);
    }
}