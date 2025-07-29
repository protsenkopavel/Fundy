package net.protsenko.fundy.notifier.bot;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.notifier.dto.AllowedChat;
import net.protsenko.fundy.notifier.dto.Role;
import net.protsenko.fundy.notifier.repo.AllowedChatRepo;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final AllowedChatRepo repo;

    public boolean allowed(long chatId) {
        return repo.existsById(chatId);
    }

    public boolean isAdmin(long chatId) {
        Optional<AllowedChat> ac = repo.findById(chatId);
        return ac.map(a -> a.getRole() == Role.ADMIN).orElse(false);
    }
}
