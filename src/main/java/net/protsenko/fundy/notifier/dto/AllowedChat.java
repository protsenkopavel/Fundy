package net.protsenko.fundy.notifier.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "allowed_chat")
@NoArgsConstructor
@AllArgsConstructor
public class AllowedChat {
    @Id
    private Long chatId;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
}
