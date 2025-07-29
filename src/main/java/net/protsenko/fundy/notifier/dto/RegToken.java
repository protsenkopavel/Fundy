package net.protsenko.fundy.notifier.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Table(name = "reg_token")
@AllArgsConstructor
@NoArgsConstructor
public class RegToken {
    @Id
    private String token;

    private Instant createdAt;
    private Instant expiresAt;
    private boolean used;
}