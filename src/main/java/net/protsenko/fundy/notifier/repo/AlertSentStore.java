package net.protsenko.fundy.notifier.repo;

import net.protsenko.fundy.notifier.dto.AlertKey;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertSentStore {
    private final Set<AlertKey> sent = ConcurrentHashMap.newKeySet();

    public boolean markIfNotSent(AlertKey key) {
        return sent.add(key);
    }
}