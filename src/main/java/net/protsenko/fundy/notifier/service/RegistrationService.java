package net.protsenko.fundy.notifier.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.notifier.dto.AllowedChat;
import net.protsenko.fundy.notifier.dto.RegToken;
import net.protsenko.fundy.notifier.dto.Role;
import net.protsenko.fundy.notifier.repo.AllowedChatRepo;
import net.protsenko.fundy.notifier.repo.RegTokenRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegTokenRepo tokenRepo;
    private final AllowedChatRepo chatRepo;

    @Transactional
    public Result register(long chatId, String token) {

        Optional<RegToken> opt = tokenRepo.findById(token);
        if (opt.isEmpty()) return Result.NO_SUCH_TOKEN;

        RegToken t = opt.get();
        if (t.isUsed() || t.getExpiresAt().isBefore(Instant.now())) {
            return Result.TOKEN_EXPIRED;
        }

        t.setUsed(true);
        tokenRepo.save(t);

        chatRepo.save(new AllowedChat(chatId, Role.USER));
        return Result.OK;
    }

    public enum Result {
        OK,
        NO_SUCH_TOKEN,
        TOKEN_EXPIRED
    }
}
