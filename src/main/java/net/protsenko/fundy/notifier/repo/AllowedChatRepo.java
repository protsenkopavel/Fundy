package net.protsenko.fundy.notifier.repo;

import net.protsenko.fundy.notifier.dto.AllowedChat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedChatRepo extends JpaRepository<AllowedChat, Long> {
}
