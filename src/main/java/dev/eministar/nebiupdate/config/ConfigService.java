package dev.eministar.nebiupdate.config;

import dev.eministar.nebiupdate.data.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigService.class);

    private final Database database;
    private final Consumer<Map<String, String>> onConfigPersist;
    private volatile BotConfig cachedConfig;

    public ConfigService(Database database) {
        this(database, null);
    }

    public ConfigService(Database database, Consumer<Map<String, String>> onConfigPersist) {
        this.database = database;
        this.onConfigPersist = onConfigPersist;
    }

    public synchronized BotConfig initialize() {
        return initialize(Map.of());
    }

    public synchronized BotConfig initialize(Map<String, String> bootstrapValues) {
        Map<String, String> merged = BotConfig.defaultMap();
        if (bootstrapValues != null) {
            merged.putAll(bootstrapValues);
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            upsert(entry.getKey(), entry.getValue());
        }

        cachedConfig = BotConfig.from(loadRawInternal());
        persistExternal(cachedConfig.toMap());
        return cachedConfig;
    }

    public BotConfig get() {
        BotConfig local = cachedConfig;
        if (local == null) {
            synchronized (this) {
                if (cachedConfig == null) {
                    cachedConfig = BotConfig.from(loadRawInternal());
                }
                return cachedConfig;
            }
        }
        return local;
    }

    public synchronized BotConfig reload() {
        cachedConfig = BotConfig.from(loadRawInternal());
        return cachedConfig;
    }

    public synchronized BotConfig updateFromMap(Map<String, Object> updates) {
        Objects.requireNonNull(updates, "updates");
        Map<String, String> raw = loadRawInternal();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            if (!BotConfig.supportedKeys().contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            raw.put(key, value.toString().trim());
        }

        BotConfig validated = BotConfig.from(raw);
        for (Map.Entry<String, String> entry : validated.toMap().entrySet()) {
            upsert(entry.getKey(), entry.getValue());
        }
        cachedConfig = validated;
        persistExternal(cachedConfig.toMap());
        LOGGER.info("Configuration updated via API/console");
        return validated;
    }

    public synchronized Map<String, String> getRawMap() {
        return new LinkedHashMap<>(get().toMap());
    }

    private Map<String, String> loadRawInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT key, value FROM app_config";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                map.put(resultSet.getString("key"), resultSet.getString("value"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load configuration", ex);
        }
        return map;
    }

    private void upsert(String key, String value) {
        String sql = "INSERT INTO app_config(key, value) VALUES(?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to persist config key: " + key, ex);
        }
    }

    private void persistExternal(Map<String, String> values) {
        if (onConfigPersist == null) {
            return;
        }
        try {
            onConfigPersist.accept(values);
        } catch (Exception ex) {
            LOGGER.warn("External config persistence failed", ex);
        }
    }
}
