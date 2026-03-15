package dev.eministar.nebiupdate.data;

import java.time.Instant;
import java.time.LocalDate;

public record WeeklyMessageRecord(
        LocalDate weekStart,
        String channelId,
        String messageId,
        Instant createdAt
) {
}
