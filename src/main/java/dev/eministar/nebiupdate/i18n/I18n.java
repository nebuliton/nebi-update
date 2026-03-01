package dev.eministar.nebiupdate.i18n;

import dev.eministar.nebiupdate.config.BotConfig;

import java.util.Map;

public final class I18n {
    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
            "de", Map.ofEntries(
                    Map.entry("renderer.added", "Neu"),
                    Map.entry("renderer.changed", "Geändert"),
                    Map.entry("renderer.removed", "Entfernt"),
                    Map.entry("renderer.none", "Keine Einträge."),
                    Map.entry("renderer.current_week", "Aktuelle Woche"),
                    Map.entry("renderer.test_prefix", "TEST"),
                    Map.entry("renderer.test_notice", "Diese Nachricht ist ein Test und wird nicht als Wochenpost gespeichert."),
                    Map.entry("renderer.no_text", "(kein Text)")
            ),
            "en", Map.ofEntries(
                    Map.entry("renderer.added", "Added"),
                    Map.entry("renderer.changed", "Changed"),
                    Map.entry("renderer.removed", "Removed"),
                    Map.entry("renderer.none", "No entries."),
                    Map.entry("renderer.current_week", "Current week"),
                    Map.entry("renderer.test_prefix", "TEST"),
                    Map.entry("renderer.test_notice", "This message is a test and will not be saved as weekly post."),
                    Map.entry("renderer.no_text", "(no text)")
            )
    );

    private I18n() {
    }

    public static String text(BotConfig config, String key, String fallback) {
        if (!config.i18nEnabled()) {
            return fallback;
        }
        String locale = normalizeLocale(config.locale());
        String fallbackLocale = normalizeLocale(config.fallbackLocale());
        String translated = lookup(locale, key);
        if (translated != null) {
            return translated;
        }
        translated = lookup(fallbackLocale, key);
        if (translated != null) {
            return translated;
        }
        return fallback;
    }

    private static String lookup(String locale, String key) {
        Map<String, String> map = TRANSLATIONS.get(locale);
        if (map == null) {
            return null;
        }
        return map.get(key);
    }

    private static String normalizeLocale(String locale) {
        if (locale == null) {
            return "de";
        }
        String normalized = locale.trim().toLowerCase();
        if (normalized.equals("en")) {
            return "en";
        }
        return "de";
    }
}
