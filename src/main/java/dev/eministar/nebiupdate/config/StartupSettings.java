package dev.eministar.nebiupdate.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record StartupSettings(
        String discordToken,
        String dbPath,
        String dashboardToken,
        Map<String, String> botConfig
) {
    public StartupSettings {
        botConfig = botConfig == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(botConfig));
    }
}
