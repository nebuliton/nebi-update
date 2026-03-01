package dev.eministar.nebiupdate.audit;

import dev.eministar.nebiupdate.data.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuditRepository {
    private final Database database;

    public AuditRepository(Database database) {
        this.database = database;
    }

    public void append(String actor, String source, String action, String entityType, String entityId, String details) {
        String sql = """
                INSERT INTO audit_log(created_at, actor, source, action, entity_type, entity_id, details)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                """;
        Instant now = Instant.now();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now.toString());
            statement.setString(2, normalize(actor));
            statement.setString(3, normalize(source));
            statement.setString(4, normalize(action));
            statement.setString(5, normalize(entityType));
            statement.setString(6, normalize(entityId));
            statement.setString(7, normalize(details));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to append audit log", ex);
        }
    }

    public void trimToMaxEntries(int maxEntries) {
        if (maxEntries <= 0) {
            return;
        }

        String sql = """
                DELETE FROM audit_log
                WHERE id NOT IN (
                    SELECT id
                    FROM audit_log
                    ORDER BY id DESC
                    LIMIT ?
                )
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, maxEntries);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to trim audit log", ex);
        }
    }

    public List<AuditEntry> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        String sql = """
                SELECT id, created_at, actor, source, action, entity_type, entity_id, details
                FROM audit_log
                ORDER BY id DESC
                LIMIT ?
                """;
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(mapEntry(resultSet));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query audit log", ex);
        }
        return entries;
    }

    public List<AuditEntry> findAll() {
        String sql = """
                SELECT id, created_at, actor, source, action, entity_type, entity_id, details
                FROM audit_log
                ORDER BY id ASC
                """;
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                entries.add(mapEntry(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query audit log", ex);
        }
        return entries;
    }

    public void replaceAll(List<AuditEntry> entries) {
        deleteAll();
        String sql = """
                INSERT INTO audit_log(id, created_at, actor, source, action, entity_type, entity_id, details)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (AuditEntry entry : entries) {
                statement.setLong(1, entry.id());
                statement.setString(2, entry.createdAt().toString());
                statement.setString(3, normalize(entry.actor()));
                statement.setString(4, normalize(entry.source()));
                statement.setString(5, normalize(entry.action()));
                statement.setString(6, normalize(entry.entityType()));
                statement.setString(7, normalize(entry.entityId()));
                statement.setString(8, normalize(entry.details()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to restore audit log", ex);
        }
    }

    public void deleteAll() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_log")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete audit log", ex);
        }
    }

    private AuditEntry mapEntry(ResultSet resultSet) throws SQLException {
        return new AuditEntry(
                resultSet.getLong("id"),
                parseInstant(resultSet.getString("created_at")),
                resultSet.getString("actor"),
                resultSet.getString("source"),
                resultSet.getString("action"),
                resultSet.getString("entity_type"),
                resultSet.getString("entity_id"),
                resultSet.getString("details")
        );
    }

    private Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }
}
