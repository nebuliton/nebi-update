package dev.eministar.nebiupdate.audit;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;

import java.util.List;
import java.util.Map;

public final class AuditService {
    private final ConfigService configService;
    private final AuditRepository auditRepository;

    public AuditService(ConfigService configService, AuditRepository auditRepository) {
        this.configService = configService;
        this.auditRepository = auditRepository;
    }

    public void log(String actor, String source, String action, String entityType, String entityId, Map<String, Object> details) {
        BotConfig config = configService.get();
        if (!config.auditEnabled()) {
            return;
        }
        String detailText = stringify(details);
        auditRepository.append(actor, source, action, entityType, entityId, detailText);
        auditRepository.trimToMaxEntries(config.auditMaxEntries());
    }

    public List<AuditEntry> recent(int limit) {
        BotConfig config = configService.get();
        if (!config.auditEnabled()) {
            return List.of();
        }
        return auditRepository.findRecent(limit);
    }

    public List<AuditEntry> all() {
        BotConfig config = configService.get();
        if (!config.auditEnabled()) {
            return List.of();
        }
        return auditRepository.findAll();
    }

    private String stringify(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        builder.append("}");
        return builder.toString();
    }
}
