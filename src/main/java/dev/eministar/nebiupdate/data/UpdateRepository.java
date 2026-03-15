package dev.eministar.nebiupdate.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateRepository {
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^\\d{15,25}$");
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("^<@!?(\\d{15,25})>$");

    private final Database database;

    public UpdateRepository(Database database) {
        this.database = database;
    }

    public UpdateEntry create(LocalDate weekStart, UpdateType type, String content, String author) {
        String sql = """
                INSERT INTO updates(week_start, type, content, author, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """;
        Instant now = Instant.now();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, weekStart.toString());
            statement.setString(2, type.key());
            statement.setString(3, content);
            statement.setString(4, normalizeAuthor(author));
            statement.setString(5, now.toString());
            statement.setString(6, now.toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    return new UpdateEntry(id, weekStart, type, content, normalizeAuthor(author), now, now);
                }
            }
            throw new IllegalStateException("Failed to obtain generated update id");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create update", ex);
        }
    }

    public List<UpdateEntry> findByWeek(LocalDate weekStart) {
        String sql = """
                SELECT *
                FROM updates
                WHERE week_start = ?
                ORDER BY
                    CASE type
                        WHEN 'added' THEN 1
                        WHEN 'changed' THEN 2
                        WHEN 'removed' THEN 3
                        ELSE 99
                    END ASC,
                    id ASC
                """;
        List<UpdateEntry> updates = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weekStart.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    updates.add(mapUpdate(resultSet));
                }
            }
            return updates;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch updates for week " + weekStart, ex);
        }
    }

    public Optional<UpdateEntry> findByIdInWeek(long id, LocalDate weekStart) {
        String sql = "SELECT * FROM updates WHERE id = ? AND week_start = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, weekStart.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUpdate(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch update by id", ex);
        }
    }

    public boolean updateInWeek(long id, LocalDate weekStart, UpdateType type, String content, String author) {
        String sql = """
                UPDATE updates
                SET type = ?, content = ?, author = ?, updated_at = ?
                WHERE id = ? AND week_start = ?
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.key());
            statement.setString(2, content);
            statement.setString(3, normalizeAuthor(author));
            statement.setString(4, Instant.now().toString());
            statement.setLong(5, id);
            statement.setString(6, weekStart.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update entry " + id, ex);
        }
    }

    public boolean deleteInWeek(long id, LocalDate weekStart) {
        String sql = "DELETE FROM updates WHERE id = ? AND week_start = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, weekStart.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete entry " + id, ex);
        }
    }

    public Optional<String> findWeeklyMessageId(LocalDate weekStart) {
        String sql = "SELECT message_id FROM weekly_messages WHERE week_start = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weekStart.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("message_id"));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read weekly message id", ex);
        }
    }

    public void upsertWeeklyMessage(LocalDate weekStart, String channelId, String messageId) {
        String sql = """
                INSERT INTO weekly_messages(week_start, channel_id, message_id, created_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(week_start)
                DO UPDATE SET channel_id = excluded.channel_id, message_id = excluded.message_id
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weekStart.toString());
            statement.setString(2, channelId);
            statement.setString(3, messageId);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert weekly message", ex);
        }
    }

    public int countByWeek(LocalDate weekStart) {
        String sql = "SELECT COUNT(1) FROM updates WHERE week_start = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weekStart.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
            return 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count updates", ex);
        }
    }

    public List<UpdateEntry> findAll() {
        String sql = """
                SELECT *
                FROM updates
                ORDER BY week_start ASC, id ASC
                """;
        List<UpdateEntry> updates = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                updates.add(mapUpdate(resultSet));
            }
            return updates;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch all updates", ex);
        }
    }

    public List<WeeklyMessageRecord> findAllWeeklyMessages() {
        String sql = """
                SELECT week_start, channel_id, message_id, created_at
                FROM weekly_messages
                ORDER BY week_start ASC
                """;
        List<WeeklyMessageRecord> records = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(mapWeeklyMessage(resultSet));
            }
            return records;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch weekly message records", ex);
        }
    }

    public List<WeeklyTypeStats> findWeeklyTypeStats(int limitWeeks) {
        int safeLimit = Math.max(1, Math.min(limitWeeks, 260));
        String sql = """
                SELECT
                    week_start,
                    SUM(CASE WHEN type = 'added' THEN 1 ELSE 0 END) AS added_count,
                    SUM(CASE WHEN type = 'changed' THEN 1 ELSE 0 END) AS changed_count,
                    SUM(CASE WHEN type = 'removed' THEN 1 ELSE 0 END) AS removed_count
                FROM updates
                GROUP BY week_start
                ORDER BY week_start DESC
                LIMIT ?
                """;
        List<WeeklyTypeStats> stats = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LocalDate weekStart = LocalDate.parse(resultSet.getString("week_start"));
                    int added = resultSet.getInt("added_count");
                    int changed = resultSet.getInt("changed_count");
                    int removed = resultSet.getInt("removed_count");
                    stats.add(new WeeklyTypeStats(weekStart, added, changed, removed));
                }
            }
            return stats.reversed();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch weekly type stats", ex);
        }
    }

    public void replaceAll(List<UpdateEntry> updates, List<WeeklyMessageRecord> weeklyMessages) {
        String deleteUpdatesSql = "DELETE FROM updates";
        String deleteWeeklyMessagesSql = "DELETE FROM weekly_messages";
        String insertUpdateSql = """
                INSERT INTO updates(id, week_start, type, content, author, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                """;
        String insertWeeklyMessageSql = """
                INSERT INTO weekly_messages(week_start, channel_id, message_id, created_at)
                VALUES(?, ?, ?, ?)
                """;

        try (Connection connection = database.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement deleteUpdates = connection.prepareStatement(deleteUpdatesSql);
                 PreparedStatement deleteWeeklyMessages = connection.prepareStatement(deleteWeeklyMessagesSql);
                 PreparedStatement insertUpdate = connection.prepareStatement(insertUpdateSql);
                 PreparedStatement insertWeeklyMessage = connection.prepareStatement(insertWeeklyMessageSql)) {
                deleteUpdates.executeUpdate();
                deleteWeeklyMessages.executeUpdate();

                for (UpdateEntry entry : updates) {
                    insertUpdate.setLong(1, entry.id());
                    insertUpdate.setString(2, entry.weekStart().toString());
                    insertUpdate.setString(3, entry.type().key());
                    insertUpdate.setString(4, entry.content());
                    insertUpdate.setString(5, normalizeAuthor(entry.author()));
                    insertUpdate.setString(6, entry.createdAt().toString());
                    insertUpdate.setString(7, entry.updatedAt().toString());
                    insertUpdate.addBatch();
                }
                insertUpdate.executeBatch();

                for (WeeklyMessageRecord record : weeklyMessages) {
                    insertWeeklyMessage.setString(1, record.weekStart().toString());
                    insertWeeklyMessage.setString(2, record.channelId());
                    insertWeeklyMessage.setString(3, record.messageId());
                    insertWeeklyMessage.setString(4, record.createdAt().toString());
                    insertWeeklyMessage.addBatch();
                }
                insertWeeklyMessage.executeBatch();

                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to replace update data", ex);
        }
    }

    private UpdateEntry mapUpdate(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        LocalDate weekStart = LocalDate.parse(resultSet.getString("week_start"));
        UpdateType type = UpdateType.fromKey(resultSet.getString("type"))
                .orElseThrow(() -> new IllegalStateException("Unsupported update type in DB"));
        String content = resultSet.getString("content");
        String author = resultSet.getString("author");
        Instant createdAt = parseInstant(resultSet.getString("created_at"));
        Instant updatedAt = parseInstant(resultSet.getString("updated_at"));
        return new UpdateEntry(id, weekStart, type, content, normalizeAuthor(author), createdAt, updatedAt);
    }

    private WeeklyMessageRecord mapWeeklyMessage(ResultSet resultSet) throws SQLException {
        LocalDate weekStart = LocalDate.parse(resultSet.getString("week_start"));
        String channelId = resultSet.getString("channel_id");
        String messageId = resultSet.getString("message_id");
        Instant createdAt = parseInstant(resultSet.getString("created_at"));
        return new WeeklyMessageRecord(weekStart, channelId, messageId, createdAt);
    }

    private Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String normalizeAuthor(String author) {
        if (author == null || author.isBlank()) {
            return "Unbekannt";
        }
        String value = author.trim();

        Matcher mentionMatcher = USER_MENTION_PATTERN.matcher(value);
        if (mentionMatcher.matches()) {
            return mentionMatcher.group(1);
        }

        if (USER_ID_PATTERN.matcher(value).matches()) {
            return value;
        }

        return value;
    }
}
