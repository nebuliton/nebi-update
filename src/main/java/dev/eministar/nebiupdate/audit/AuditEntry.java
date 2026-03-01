package dev.eministar.nebiupdate.audit;

import java.time.Instant;

public record AuditEntry(
        long id,
        Instant createdAt,
        String actor,
        String source,
        String action,
        String entityType,
        String entityId,
        String details
) {
}
