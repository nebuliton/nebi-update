package dev.eministar.nebiupdate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlConfigManager.class);

    private final Path configPath;

    public YamlConfigManager(Path configPath) {
        this.configPath = configPath.toAbsolutePath();
    }

    public synchronized StartupSettings loadSettings() {
        ensureFileExists();
        Map<String, Object> root = loadRoot();
        Map<String, String> botConfig = readBotConfig(root);
        String dbPath = readString(root, "app", "db_path");
        if (dbPath.isBlank()) {
            dbPath = "data/nebiupdate.db";
        }
        return new StartupSettings(
                readString(root, "discord", "token"),
                dbPath,
                readString(root, "dashboard", "token"),
                botConfig
        );
    }

    public synchronized void saveBotConfig(Map<String, String> values) {
        try {
            ensureFileExists();
            Map<String, Object> root = loadRoot();

            Map<String, Object> discord = section(root, "discord");
            discord.put("guild_id", value(values, BotConfig.KEY_GUILD_ID));
            discord.put("channel_id", value(values, BotConfig.KEY_CHANNEL_ID));

            Map<String, Object> schedule = section(root, "schedule");
            schedule.put("timezone", value(values, BotConfig.KEY_TIMEZONE));
            schedule.put("day", value(values, BotConfig.KEY_SCHEDULE_DAY));
            schedule.put("time", value(values, BotConfig.KEY_SCHEDULE_TIME));

            Map<String, Object> dashboard = section(root, "dashboard");
            dashboard.put("host", value(values, BotConfig.KEY_DASHBOARD_HOST));
            dashboard.put("port", parsePort(value(values, BotConfig.KEY_DASHBOARD_PORT)));

            Map<String, Object> messages = section(root, "messages");
            messages.put("title_emoji", value(values, BotConfig.KEY_TITLE_EMOJI));
            messages.put("title_emoji_id", value(values, BotConfig.KEY_TITLE_EMOJI_ID));
            messages.put("title_emoji_animated", bool(values, BotConfig.KEY_TITLE_EMOJI_ANIMATED));
            messages.put("title_text", value(values, BotConfig.KEY_TITLE_TEXT));
            messages.put("added_emoji", value(values, BotConfig.KEY_ADDED_EMOJI));
            messages.put("added_emoji_id", value(values, BotConfig.KEY_ADDED_EMOJI_ID));
            messages.put("added_emoji_animated", bool(values, BotConfig.KEY_ADDED_EMOJI_ANIMATED));
            messages.put("changed_emoji", value(values, BotConfig.KEY_CHANGED_EMOJI));
            messages.put("changed_emoji_id", value(values, BotConfig.KEY_CHANGED_EMOJI_ID));
            messages.put("changed_emoji_animated", bool(values, BotConfig.KEY_CHANGED_EMOJI_ANIMATED));
            messages.put("removed_emoji", value(values, BotConfig.KEY_REMOVED_EMOJI));
            messages.put("removed_emoji_id", value(values, BotConfig.KEY_REMOVED_EMOJI_ID));
            messages.put("removed_emoji_animated", bool(values, BotConfig.KEY_REMOVED_EMOJI_ANIMATED));
            messages.put("notice_emoji", value(values, BotConfig.KEY_NOTICE_EMOJI));
            messages.put("notice_emoji_id", value(values, BotConfig.KEY_NOTICE_EMOJI_ID));
            messages.put("notice_emoji_animated", bool(values, BotConfig.KEY_NOTICE_EMOJI_ANIMATED));
            messages.put("notice_text", value(values, BotConfig.KEY_NOTICE_TEXT));
            messages.put("no_change_text", value(values, BotConfig.KEY_NO_CHANGE_TEXT));
            messages.put("spacer", value(values, BotConfig.KEY_SPACER));

            writeRoot(root);
        } catch (Exception ex) {
            LOGGER.warn("Failed to sync bot config to {}", configPath, ex);
        }
    }

    private int parsePort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ex) {
            return 8080;
        }
    }

    private String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private boolean bool(Map<String, String> values, String key) {
        String raw = values.getOrDefault(key, "");
        return raw.equalsIgnoreCase("true")
                || raw.equals("1")
                || raw.equalsIgnoreCase("yes")
                || raw.equalsIgnoreCase("ja")
                || raw.equalsIgnoreCase("on");
    }

    private Map<String, String> readBotConfig(Map<String, Object> root) {
        Map<String, String> botConfig = BotConfig.defaultMap();
        botConfig.put(BotConfig.KEY_GUILD_ID, readString(root, "discord", "guild_id"));
        botConfig.put(BotConfig.KEY_CHANNEL_ID, readString(root, "discord", "channel_id"));
        botConfig.put(BotConfig.KEY_TIMEZONE, fallback(readString(root, "schedule", "timezone"), botConfig.get(BotConfig.KEY_TIMEZONE)));
        botConfig.put(BotConfig.KEY_SCHEDULE_DAY, fallback(readString(root, "schedule", "day"), botConfig.get(BotConfig.KEY_SCHEDULE_DAY)));
        botConfig.put(BotConfig.KEY_SCHEDULE_TIME, fallback(readString(root, "schedule", "time"), botConfig.get(BotConfig.KEY_SCHEDULE_TIME)));
        botConfig.put(BotConfig.KEY_DASHBOARD_HOST, fallback(readString(root, "dashboard", "host"), botConfig.get(BotConfig.KEY_DASHBOARD_HOST)));
        String portRaw = readString(root, "dashboard", "port");
        if (!portRaw.isBlank()) {
            botConfig.put(BotConfig.KEY_DASHBOARD_PORT, portRaw);
        }

        botConfig.put(BotConfig.KEY_TITLE_EMOJI, fallback(readString(root, "messages", "title_emoji"), botConfig.get(BotConfig.KEY_TITLE_EMOJI)));
        botConfig.put(BotConfig.KEY_TITLE_EMOJI_ID, fallback(readString(root, "messages", "title_emoji_id"), botConfig.get(BotConfig.KEY_TITLE_EMOJI_ID)));
        botConfig.put(BotConfig.KEY_TITLE_EMOJI_ANIMATED, fallback(readString(root, "messages", "title_emoji_animated"), botConfig.get(BotConfig.KEY_TITLE_EMOJI_ANIMATED)));
        botConfig.put(BotConfig.KEY_TITLE_TEXT, fallback(readString(root, "messages", "title_text"), botConfig.get(BotConfig.KEY_TITLE_TEXT)));
        botConfig.put(BotConfig.KEY_ADDED_EMOJI, fallback(readString(root, "messages", "added_emoji"), botConfig.get(BotConfig.KEY_ADDED_EMOJI)));
        botConfig.put(BotConfig.KEY_ADDED_EMOJI_ID, fallback(readString(root, "messages", "added_emoji_id"), botConfig.get(BotConfig.KEY_ADDED_EMOJI_ID)));
        botConfig.put(BotConfig.KEY_ADDED_EMOJI_ANIMATED, fallback(readString(root, "messages", "added_emoji_animated"), botConfig.get(BotConfig.KEY_ADDED_EMOJI_ANIMATED)));
        botConfig.put(BotConfig.KEY_CHANGED_EMOJI, fallback(readString(root, "messages", "changed_emoji"), botConfig.get(BotConfig.KEY_CHANGED_EMOJI)));
        botConfig.put(BotConfig.KEY_CHANGED_EMOJI_ID, fallback(readString(root, "messages", "changed_emoji_id"), botConfig.get(BotConfig.KEY_CHANGED_EMOJI_ID)));
        botConfig.put(BotConfig.KEY_CHANGED_EMOJI_ANIMATED, fallback(readString(root, "messages", "changed_emoji_animated"), botConfig.get(BotConfig.KEY_CHANGED_EMOJI_ANIMATED)));
        botConfig.put(BotConfig.KEY_REMOVED_EMOJI, fallback(readString(root, "messages", "removed_emoji"), botConfig.get(BotConfig.KEY_REMOVED_EMOJI)));
        botConfig.put(BotConfig.KEY_REMOVED_EMOJI_ID, fallback(readString(root, "messages", "removed_emoji_id"), botConfig.get(BotConfig.KEY_REMOVED_EMOJI_ID)));
        botConfig.put(BotConfig.KEY_REMOVED_EMOJI_ANIMATED, fallback(readString(root, "messages", "removed_emoji_animated"), botConfig.get(BotConfig.KEY_REMOVED_EMOJI_ANIMATED)));
        botConfig.put(BotConfig.KEY_NOTICE_EMOJI, fallback(readString(root, "messages", "notice_emoji"), botConfig.get(BotConfig.KEY_NOTICE_EMOJI)));
        botConfig.put(BotConfig.KEY_NOTICE_EMOJI_ID, fallback(readString(root, "messages", "notice_emoji_id"), botConfig.get(BotConfig.KEY_NOTICE_EMOJI_ID)));
        botConfig.put(BotConfig.KEY_NOTICE_EMOJI_ANIMATED, fallback(readString(root, "messages", "notice_emoji_animated"), botConfig.get(BotConfig.KEY_NOTICE_EMOJI_ANIMATED)));
        botConfig.put(BotConfig.KEY_NOTICE_TEXT, fallback(readString(root, "messages", "notice_text"), botConfig.get(BotConfig.KEY_NOTICE_TEXT)));
        botConfig.put(BotConfig.KEY_NO_CHANGE_TEXT, fallback(readString(root, "messages", "no_change_text"), botConfig.get(BotConfig.KEY_NO_CHANGE_TEXT)));
        botConfig.put(BotConfig.KEY_SPACER, fallback(readString(root, "messages", "spacer"), botConfig.get(BotConfig.KEY_SPACER)));
        return botConfig;
    }

    private String fallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private void ensureFileExists() {
        try {
            if (Files.exists(configPath)) {
                return;
            }
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    configPath,
                    defaultYaml(),
                    StandardOpenOption.CREATE_NEW
            );
            LOGGER.info("Created default config file at {}", configPath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create config file: " + configPath, ex);
        }
    }

    private Map<String, Object> loadRoot() {
        try {
            String raw = Files.readString(configPath);
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(raw);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> casted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        casted.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return casted;
            }
            return new LinkedHashMap<>();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read config file: " + configPath, ex);
        }
    }

    private void writeRoot(Map<String, Object> root) {
        try {
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setPrettyFlow(true);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            String content = "# NebiUpdate configuration\n" + yaml.dump(root);
            Files.writeString(
                    configPath,
                    content,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write config file: " + configPath, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> root, String name) {
        Object current = root.get(name);
        if (current instanceof Map<?, ?> map) {
            Map<String, Object> casted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    casted.put(entry.getKey().toString(), entry.getValue());
                }
            }
            root.put(name, casted);
            return casted;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(name, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private String readString(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return "";
            }
            current = map.get(key);
            if (current == null) {
                return "";
            }
        }
        return current.toString().trim();
    }

    private String defaultYaml() {
        return """
                # NebiUpdate configuration
                app:
                  db_path: data/nebiupdate.db

                discord:
                  token: ""
                  guild_id: ""
                  channel_id: ""

                dashboard:
                  host: 0.0.0.0
                  port: 8080
                  token: ""

                schedule:
                  timezone: Europe/Berlin
                  day: MONDAY
                  time: "12:00"

                messages:
                  title_emoji: ":gameboy:"
                  title_emoji_id: ""
                  title_emoji_animated: false
                  title_text: "WOCHEN-RÜCKBLICK"
                  added_emoji: ":PointGreen:"
                  added_emoji_id: ""
                  added_emoji_animated: false
                  changed_emoji: ":PointOrange:"
                  changed_emoji_id: ""
                  changed_emoji_animated: false
                  removed_emoji: ":PointRed:"
                  removed_emoji_id: ""
                  removed_emoji_animated: false
                  notice_emoji: ":arrow2:"
                  notice_emoji_id: ""
                  notice_emoji_animated: false
                  notice_text: "Diese Nachricht wird mit jeder neuen Änderung dieser Woche bearbeitet."
                  no_change_text: "In dieser Woche gab es keine Änderung."
                  spacer: " "
                """;
    }
}
