package net.protsenko.fundy.notifier.repo;

import net.protsenko.fundy.notifier.dto.RegToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegTokenRepo extends JpaRepository<RegToken, String> {
}
