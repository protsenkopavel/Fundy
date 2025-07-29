package net.protsenko.fundy.notifier.dto;

import java.time.Instant;

public record SnapshotRefreshedEvent(Instant when) {}
