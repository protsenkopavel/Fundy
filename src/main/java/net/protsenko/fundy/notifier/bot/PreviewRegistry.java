package net.protsenko.fundy.notifier.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PreviewRegistry {

    private final Map<Long, Integer> map = new ConcurrentHashMap<>();

    public void put(long chatId, int msgId) {
        map.put(chatId, msgId);
    }

    public Optional<Integer> get(long chatId) {
        return Optional.ofNullable(map.get(chatId));
    }

    public Map<Long, Integer> all() {
        return Map.copyOf(map);
    }

    public void remove(long chatId) {
        map.remove(chatId);
    }
}
