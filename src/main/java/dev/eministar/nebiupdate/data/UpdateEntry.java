package dev.eministar.nebiupdate.data;

import java.time.Instant;
import java.time.LocalDate;

public record UpdateEntry(
        long id,
        LocalDate weekStart,
        UpdateType type,
        String content,
        String author,
        Instant createdAt,
        Instant updatedAt
) {
}
