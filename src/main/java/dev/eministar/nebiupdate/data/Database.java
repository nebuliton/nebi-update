package dev.eministar.nebiupdate.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    private final HikariDataSource dataSource;

    public Database(String dbPath) {
        String resolvedPath = resolveDbPath(dbPath);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + resolvedPath);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setPoolName("nebi-sqlite");
        dataSource = new HikariDataSource(config);
    }

    private static String resolveDbPath(String dbPath) {
        try {
            Path path = Path.of(dbPath).toAbsolutePath();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return path.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve database path: " + dbPath, ex);
        }
    }

    public void initialize() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_config(
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS updates(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        week_start TEXT NOT NULL,
                        type TEXT NOT NULL CHECK(type IN ('added','changed','removed')),
                        content TEXT NOT NULL,
                        author TEXT NOT NULL DEFAULT 'Unbekannt',
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            ensureColumn(statement, "updates", "author", "TEXT NOT NULL DEFAULT 'Unbekannt'");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_updates_week_start ON updates(week_start)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS weekly_messages(
                        week_start TEXT PRIMARY KEY,
                        channel_id TEXT NOT NULL,
                        message_id TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_log(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        source TEXT NOT NULL,
                        action TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        entity_id TEXT NOT NULL,
                        details TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_log(created_at)");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize database schema", ex);
        }
        LOGGER.info("Database schema initialized");
    }

    private void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            LOGGER.info("Added missing column {}.{}.", table, column);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!message.contains("duplicate column name")) {
                throw ex;
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
