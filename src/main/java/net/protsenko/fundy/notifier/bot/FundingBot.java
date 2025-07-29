package net.protsenko.fundy.notifier.bot;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import net.protsenko.fundy.notifier.dto.SnapshotRefreshedEvent;
import net.protsenko.fundy.notifier.repo.UserSettingsRepo;
import net.protsenko.fundy.notifier.service.FundingSnapshotCache;
import net.protsenko.fundy.notifier.service.RegistrationService;
import net.protsenko.fundy.notifier.service.TokenService;
import net.protsenko.fundy.notifier.util.FundingMessageFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class FundingBot extends TelegramLongPollingBot {

    /* ---------- Константы ---------- */

    private static final Pattern DURATION_RE =
            Pattern.compile("(?i)^\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*$");

    private static final Pattern UUID_RE =
            Pattern.compile(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                    Pattern.CASE_INSENSITIVE);

    /* ---------- DI‑поля ---------- */

    private final String username;
    private final String token;
    private final UserSettingsRepo repo;
    private final BotStateStore stateStore;
    private final FundingSnapshotCache cache;
    private final Executor previewExecutor;
    private final PreviewRegistry previewRegistry;
    private final AccessGuard accessGuard;
    private final TokenService tokenService;
    private final RegistrationService registrationService;

    /* ---------- Конструктор ---------- */

    public FundingBot(
            @Value("${telegram.bot.username}") String username,
            @Value("${telegram.bot.token}") String token,
            UserSettingsRepo repo,
            BotStateStore stateStore,
            FundingSnapshotCache cache,
            Executor previewExecutor,
            PreviewRegistry previewRegistry,
            AccessGuard accessGuard,
            TokenService tokenService,
            RegistrationService registrationService
    ) {
        this.username = username;
        this.token = token;
        this.repo = repo;
        this.stateStore = stateStore;
        this.cache = cache;
        this.previewExecutor = previewExecutor;
        this.previewRegistry = previewRegistry;
        this.accessGuard = accessGuard;
        this.tokenService = tokenService;
        this.registrationService = registrationService;
    }

    /* ---------- Life‑cycle ---------- */

    @PostConstruct
    void init() {
        log.info("FundingBot init: username={}, tokenPresent={}",
                username, token != null && !token.isBlank());
    }

    /**
     * Регистрируем список slash‑команд (виден в UI Telegram).
     */
    @PostConstruct
    void registerCommands() {
        var commands = List.of(
                new BotCommand("start", "Запустить бота"),
                new BotCommand("menu", "Открыть меню настроек"),
                new BotCommand("top", "Показать топ фандингов сейчас"),
                new BotCommand("min", "Мин. ставка (%)"),
                new BotCommand("before", "Время до начисления"),
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

    /* ---------- Telegram overrides ---------- */

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = extractChatId(update);

        boolean registerCmd = isRegistrationCommand(update);
        boolean rawUuidToken = looksLikeUuidToken(update);

        // блокируем всё, кроме регистрации, для незарегистрированных
        boolean startReg = isStartWithReg(update);

        if (chatId != 0 && !accessGuard.allowed(chatId)
                && !(registerCmd || rawUuidToken || startReg)) {
            send(chatId, "⛔️ Доступ запрещён.\n" +
                    "Пришлите регистрационный токен одной строкой\n" +
                    "или команду /register <токен>.", null);
            return;
        }

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

    /* ---------- Helpers: Update‑разбор ---------- */

    private long extractChatId(Update upd) {
        if (upd.hasCallbackQuery()) return upd.getCallbackQuery().getMessage().getChatId();
        if (upd.hasMessage()) return upd.getMessage().getChatId();
        return 0;
    }

    private boolean isRegistrationCommand(Update upd) {
        return upd.hasMessage()
                && upd.getMessage().hasText()
                && upd.getMessage().getText().trim()
                .toLowerCase(Locale.ROOT)
                .startsWith("/register");
    }

    private boolean looksLikeUuidToken(Update upd) {
        return upd.hasMessage()
                && upd.getMessage().hasText()
                && UUID_RE.matcher(upd.getMessage().getText().trim()).matches();
    }

    /* =======================================================================
       TEXT‑команды
       ======================================================================= */

    private void handleText(Update upd) {
        long chatId = upd.getMessage().getChatId();
        String text = upd.getMessage().getText().trim();

        // если пользователь не зарегистрирован, но прислал UUID — регистрируем
        if (!accessGuard.allowed(chatId) && UUID_RE.matcher(text).matches()) {
            handleRegister(chatId, text);
            return;
        }

        /* --- разбор команды ------------------------------------------------ */

        String[] parts = text.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        // любая slash‑команда сбрасывает state‑машину
        if (text.startsWith("/")) stateStore.set(chatId, BotState.NONE);

        // если ждём «ручной ввод» (ставка / интервал / TZ)
        BotState st = stateStore.get(chatId);
        if (st != BotState.NONE && !text.startsWith("/")) {
            processWaitingState(chatId, text, st);
            return;
        }

        switch (cmd) {
            case "/start" -> handleStart(chatId, args);
            case "/menu" -> sendMenuNew(chatId);
            case "/top" -> previewAsync(chatId);
            case "/min" -> handleSlashMin(chatId, args);
            case "/before" -> handleSlashBefore(chatId, args);
            case "/tz" -> handleSlashTz(chatId, args);
            case "/exchanges" -> showExchangeToggles(chatId, null);
            case "/help" -> send(chatId, helpText(), null);
            case "/stop" -> send(chatId, "Ок, не буду слать уведомления. (/start чтобы включить)", null);
            case "/ping" -> send(chatId, "pong", null);
            case "/register" -> handleRegister(chatId, args);
            case "/newtoken" -> handleNewToken(chatId, args);
            default -> send(chatId, "Команда не распознана. Попробуй /help", null);
        }
    }

    private void processWaitingState(long chatId, String text, BotState st) {
        FundingAlertSettings old = repo.getOrDefault(chatId);
        try {
            switch (st) {
                case WAIT_MIN -> {
                    BigDecimal p = new BigDecimal(text).divide(BigDecimal.valueOf(100));
                    repo.save(old.withMinAbsRate(p));
                    send(chatId, "Мин. ставка: " + p.multiply(BigDecimal.valueOf(100)) + "%", null);
                }
                case WAIT_BEFORE -> {
                    Duration d = parseDuration(text);
                    repo.save(old.withNotifyBefore(d));
                    send(chatId, "Время до начисления: " + FundingMessageFormatter.prettyDuration(d), null);
                }
                case WAIT_TZ -> {
                    ZoneId z = ZoneId.of(text);
                    repo.save(old.withZone(z));
                    send(chatId, "Часовой пояс: " + z, null);
                }
            }
            stateStore.set(chatId, BotState.NONE);
            sendMenuNew(chatId);
        } catch (Exception e) {
            send(chatId, "Не понял ввод. Попробуй ещё раз.", null);
        }
    }

    /* ---------- /start ---------- */

    private void handleStart(long chatId, String args) {
        // deep‑link: https://t.me/…?start=reg_<uuid>
        if (args.startsWith("reg_")) {
            String raw = args.substring(4).trim();
            switch (registrationService.register(chatId, raw)) {
                case OK -> {
                    send(chatId, "✅ Регистрация успешна!", null);
                    sendMenuNew(chatId);
                }
                default -> send(chatId, "⛔️ Токен неверен или просрочен.", null);
            }
            return;
        }
        // обычный /start
        repo.save(repo.getOrDefault(chatId));
        send(chatId, "Привет! Я буду присылать фандинги. Нажми /menu", null);
    }

    private boolean isStartWithReg(Update upd) {
        return upd.hasMessage()
                && upd.getMessage().hasText()
                && upd.getMessage().getText().trim().toLowerCase(Locale.ROOT)
                .startsWith("/start reg_");
    }

    /* ---------- регистрация ---------- */

    private void handleRegister(long chatId, String token) {
        switch (registrationService.register(chatId, token)) {
            case OK -> {
                send(chatId, "✅ Регистрация успешна!", null);
                sendMenuNew(chatId);
            }
            case NO_SUCH_TOKEN -> send(chatId, "⛔️ Неверный токен.", null);
            case TOKEN_EXPIRED -> send(chatId, "⛔️ Токен просрочен или уже использован.", null);
        }
    }

    /* ---------- генерация токена админом ---------- */

    private void handleNewToken(long chatId, String args) {
        if (!accessGuard.isAdmin(chatId)) {
            send(chatId, "Команда доступна только администраторам.", null);
            return;
        }
        Duration ttl = parseDurationSafe(args, Duration.ofHours(24));
        String link = tokenService.createDeepLink(getBotUsername(), ttl);
        send(chatId, "Ссылка на регистрацию (%d ч):\n".formatted(ttl.toHours()) + link, null);
    }

    /* ---------- /min, /before, /tz ---------- */

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
            send(chatId, "Пример: /before 30m", null);
            return;
        }
        try {
            Duration d = parseDuration(args);
            var old = repo.getOrDefault(chatId);
            repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), old.exchanges(), d, old.zone()));
            send(chatId, "Время до начисления: " + FundingMessageFormatter.prettyDuration(d), null);
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

    /* ---------- /help ---------- */

    private String helpText() {
        return """
                Доступные команды:
                /menu — меню настроек
                /top — топ фандингов сейчас
                /min <pct> — мин. ставка, % (например /min 0.7)
                /before <30m|1h> — время до начисления начисления
                /tz <ZoneId> — часовой пояс (Europe/Moscow)
                /exchanges — выбрать биржи
                /stop — перестать слать уведомления
                """;
    }

    /* =======================================================================
       CALLBACK‑query
       ======================================================================= */

    private void handleCallback(Update upd) throws Exception {
        var cb = upd.getCallbackQuery();
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        String data = cb.getData();

        execute(AnswerCallbackQuery.builder().callbackQueryId(cb.getId()).build());

        if ("BACK_MENU".equals(data)) {
            editMenu(chatId, msgId);
            return;
        }

        switch (data) {
            case "SET_MIN" -> {
                stateStore.set(chatId, BotState.WAIT_MIN);
                edit(chatId, msgId, "Введи минимальную ставку, напр. 0.5", null);
            }
            case "SET_EXCH" -> showExchangeToggles(chatId, msgId);
            case "SET_BEFORE" -> {
                stateStore.set(chatId, BotState.WAIT_BEFORE);
                edit(chatId, msgId, "Введи интервал, напр. 30m или 1h", null);
            }
            case "SET_TZ" -> showTzChoices(chatId, msgId);
            case "PREVIEW" -> {
                refreshPreview(chatId, msgId);
            }
            case "ADMIN_MENU" -> showAdminMenu(chatId, msgId);
            case "ADMIN_NEW_TOKEN" -> {
                String url = tokenService.createDeepLink(getBotUsername(), Duration.ofHours(24));
                send(chatId, "Ссылка для регистрации (24 ч):\n" + url, null);
            }
            case "SHOW_REGISTER" -> edit(chatId, msgId, "Отправьте /register &lt;токен&gt;", null);
            default -> {
                if (data.startsWith("TZ_")) {
                    ZoneId z = ZoneId.of(data.substring(3));
                    FundingAlertSettings s = repo.getOrDefault(chatId).withZone(z);
                    repo.save(s);
                    editMenu(chatId, msgId);
                }
                if (data.startsWith("EX_")) {
                    toggleExchange(chatId, data.substring(3));
                    showExchangeToggles(chatId, msgId);
                }
            }
        }
    }

    /* ---------- Админ‑меню ---------- */

    private void showAdminMenu(long chatId, Integer msgId) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(List.of(
                List.of(btn("Новый токен (24ч)", "ADMIN_NEW_TOKEN")),
                List.of(btn("⬅️ Назад", "BACK_MENU"))
        ));
        edit(chatId, msgId, "Админ‑меню:", kb);
    }

    /* =======================================================================
       Preview‑блок
       ======================================================================= */

    private InlineKeyboardMarkup previewKb() {
        return new InlineKeyboardMarkup(List.of(
                List.of(btn("📋 Меню", "BACK_MENU"), btn("🔄 Обновить", "PREVIEW"))
        ));
    }

    private void previewAsync(long chatId) {
        Message pending;
        try {
            pending = execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("⏳ Загружаю топ‑фандинги…")
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(previewKb())
                    .build());
        } catch (Exception e) {
            log.warn("Cannot send pending msg", e);
            return;
        }

        int msgId = pending.getMessageId();
        previewRegistry.put(chatId, msgId);

        previewExecutor.execute(() -> {
            try {
                edit(chatId, msgId, buildPreviewText(chatId), previewKb());
            } catch (Exception e) {
                log.error("preview failed", e);
                edit(chatId, msgId, "Ошибка при получении данных. Попробуй позже.", previewKb());
            }
        });
    }

    @EventListener(SnapshotRefreshedEvent.class)
    public void onSnapshotRefresh(SnapshotRefreshedEvent ev) {
        previewRegistry.all().forEach((chatId, msgId) ->
                previewExecutor.execute(() -> {
                    try {
                        edit(chatId, msgId, buildPreviewText(chatId), previewKb());
                    } catch (Exception e) {
                        log.warn("Auto refresh failed for chat {}", chatId, e);
                    }
                }));
    }

    private void refreshPreview(long chatId, int msgId) {
        previewExecutor.execute(() -> {
            try {
                String txt = buildPreviewText(chatId);
                edit(chatId, msgId, txt, previewKb());
            } catch (Exception e) {
                log.error("refresh preview failed", e);
                edit(chatId, msgId,
                        "Ошибка при получении данных. Попробуй позже.", previewKb());
            }
        });
    }

    private String buildPreviewText(long chatId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);

        Map<ExchangeType, List<FundingRateData>> snap = cache.getLastSnapshot();
        if (snap.isEmpty() || cache.isStale())
            snap = cache.forceRefresh(Duration.ofSeconds(15));
        if (snap.isEmpty())
            return "Данные обновляются, подождите пару минут";

        var list = snap.entrySet().stream()
                .filter(e -> s.exchanges().isEmpty() || s.exchanges().contains(e.getKey()))
                .flatMap(e -> e.getValue().stream().map(fr -> Map.entry(e.getKey(), fr)))
                .filter(e -> e.getValue().fundingRate().abs().compareTo(s.minAbsRate()) >= 0)
                .sorted(Comparator.comparing(
                        (Map.Entry<ExchangeType, FundingRateData> e)
                                -> e.getValue().fundingRate().abs()).reversed())
                .limit(10)
                .toList();

        String header = "Текущие фандинги > %s%%\n\n".formatted(
                s.minAbsRate().multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString());

        if (list.isEmpty()) return header + "Нет подходящих ставок.";

        StringBuilder sb = new StringBuilder(header);
        for (var e : list) {
            sb.append(FundingMessageFormatter.format(e.getValue(), e.getKey(), s.zone()))
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /* =======================================================================
       Меню / UI
       ======================================================================= */

    private void sendMenuNew(long chatId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);
        send(chatId, menuText(s), menuKb(chatId));
    }

    private void editMenu(long chatId, Integer msgId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);
        edit(chatId, msgId, menuText(s), menuKb(chatId));
    }

    private String menuText(FundingAlertSettings s) {
        return """
                ⚙️ Настройки:
                • Мин. ставка: %s
                • Биржи: %s
                • Время до начисления: %s
                • Часовой пояс: %s
                """.formatted(
                FundingMessageFormatter.pct(s.minAbsRate()),
                s.exchanges().isEmpty() ? "ВСЕ" : s.exchanges(),
                FundingMessageFormatter.prettyDuration(s.notifyBefore()),
                s.zone());
    }

    private InlineKeyboardMarkup menuKb(long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("📉 Мин. %", "SET_MIN")));
        rows.add(List.of(btn("🏦 Биржи", "SET_EXCH")));
        rows.add(List.of(btn("⏰ Время до начисления", "SET_BEFORE")));
        rows.add(List.of(btn("🌍 Timezone", "SET_TZ")));
        rows.add(List.of(btn("👀 Топ сейчас", "PREVIEW")));
        if (accessGuard.isAdmin(chatId))
            rows.add(List.of(btn("⚙️ Админ‑меню", "ADMIN_MENU")));
        return new InlineKeyboardMarkup(rows);
    }

    /* ---------- Exchange toggles ---------- */

    private void showExchangeToggles(long chatId, Integer msgId) {
        FundingAlertSettings s = repo.getOrDefault(chatId);

        EnumSet<ExchangeType> sel = s.exchanges().isEmpty()
                ? EnumSet.noneOf(ExchangeType.class)
                : EnumSet.copyOf(s.exchanges());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (ExchangeType ex : ExchangeType.values()) {
            boolean on = sel.isEmpty() || sel.contains(ex);
            rows.add(List.of(btn((on ? "✅ " : "❌ ") + ex.name(), "EX_" + ex.name())));
        }
        rows.add(List.of(btn("⬅️ Назад", "BACK_MENU")));

        edit(chatId, msgId,
                "Биржи (нажимай, чтобы переключать):",
                new InlineKeyboardMarkup(rows));
    }

    private void toggleExchange(long chatId, String exStr) {
        ExchangeType ex = ExchangeType.valueOf(exStr);
        FundingAlertSettings old = repo.getOrDefault(chatId);
        Set<ExchangeType> set = new HashSet<>(old.exchanges());
        if (set.isEmpty()) set.addAll(Arrays.asList(ExchangeType.values()));
        if (!set.add(ex)) set.remove(ex);
        repo.save(new FundingAlertSettings(chatId, old.minAbsRate(), set, old.notifyBefore(), old.zone()));
    }

    private void showTzChoices(long chatId, int msgId) {
        List<String> zones = List.of("UTC", "Europe/Moscow", "Europe/London",
                "America/New_York", "Asia/Shanghai");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String z : zones)
            rows.add(List.of(btn(z, "TZ_" + z)));

        rows.add(List.of(btn("⬅️ Назад", "BACK_MENU")));
        edit(chatId, msgId, "Выбери часовой пояс:", new InlineKeyboardMarkup(rows));
    }

    /* =======================================================================
       Утилиты / общие методы
       ======================================================================= */

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
            em.setReplyMarkup(kb);

            execute(em);

        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            if (e.getApiResponse() == null ||
                    !e.getApiResponse().contains("message is not modified")) {
                log.warn("Edit failed", e);
            }
        } catch (Exception e) {
            log.warn("Edit failed", e);
        }
    }

    private Duration parseDuration(String src) {
        Matcher m = DURATION_RE.matcher(src);
        if (!m.matches()) throw new IllegalArgumentException("Bad duration");
        long h = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
        long mnt = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
        if (h == 0 && mnt == 0) throw new IllegalArgumentException("Zero duration");
        return Duration.ofHours(h).plusMinutes(mnt);
    }

    private Duration parseDurationSafe(String s, Duration def) {
        try {
            return (s == null || s.isBlank()) ? def : parseDuration(s);
        } catch (Exception e) {
            return def;
        }
    }
}