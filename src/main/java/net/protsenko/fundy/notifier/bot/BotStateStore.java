package net.protsenko.fundy.notifier.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class BotStateStore {
    private final Map<Long, BotState> map = new ConcurrentHashMap<>();
    public BotState get(long chat) { return map.getOrDefault(chat, BotState.NONE); }
    public void set(long chat, BotState st) { map.put(chat, st); }
}
