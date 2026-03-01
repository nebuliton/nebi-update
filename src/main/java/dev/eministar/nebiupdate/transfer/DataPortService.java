package dev.eministar.nebiupdate.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.eministar.nebiupdate.audit.AuditEntry;
import dev.eministar.nebiupdate.audit.AuditRepository;
import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.data.UpdateType;
import dev.eministar.nebiupdate.data.WeeklyMessageRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DataPortService {
    private static final DateTimeFormatter BACKUP_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String CSV_HEADER = "id,week_start,type,content,author,created_at,updated_at";

    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public DataPortService(ConfigService configService, UpdateRepository updateRepository, AuditRepository auditRepository) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String exportJson(boolean includeAudit) {
        Snapshot snapshot = snapshot(includeAudit);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize JSON export", ex);
        }
    }

    public String exportCsv(List<UpdateEntry> updates) {
        StringBuilder builder = new StringBuilder();
        builder.append(CSV_HEADER).append("\n");
        for (UpdateEntry entry : updates) {
            builder.append(entry.id()).append(",");
            builder.append(csv(entry.weekStart().toString())).append(",");
            builder.append(csv(entry.type().key())).append(",");
            builder.append(csv(entry.content())).append(",");
            builder.append(csv(entry.author())).append(",");
            builder.append(csv(entry.createdAt().toString())).append(",");
            builder.append(csv(entry.updatedAt().toString())).append("\n");
        }
        return builder.toString();
    }

    public ImportResult importJson(String payload, boolean replaceData, boolean replaceConfig, boolean replaceAudit) {
        Snapshot snapshot;
        try {
            snapshot = objectMapper.readValue(payload, Snapshot.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload for import", ex);
        }

        List<UpdateEntry> updates = new ArrayList<>();
        for (SnapshotUpdate update : snapshot.updates()) {
            UpdateType type = UpdateType.fromKey(update.type())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid update type in import: " + update.type()));
            updates.add(new UpdateEntry(
                    update.id(),
                    LocalDate.parse(update.weekStart()),
                    type,
                    nonNull(update.content()),
                    nonNull(update.author()),
                    parseInstant(update.createdAt()),
                    parseInstant(update.updatedAt())
            ));
        }

        List<WeeklyMessageRecord> weeklyMessages = new ArrayList<>();
        for (SnapshotWeeklyMessage weeklyMessage : snapshot.weeklyMessages()) {
            weeklyMessages.add(new WeeklyMessageRecord(
                    LocalDate.parse(weeklyMessage.weekStart()),
                    nonNull(weeklyMessage.channelId()),
                    nonNull(weeklyMessage.messageId()),
                    parseInstant(weeklyMessage.createdAt())
            ));
        }

        List<AuditEntry> auditEntries = new ArrayList<>();
        for (SnapshotAuditEntry auditEntry : snapshot.auditLog()) {
            auditEntries.add(new AuditEntry(
                    auditEntry.id(),
                    parseInstant(auditEntry.createdAt()),
                    nonNull(auditEntry.actor()),
                    nonNull(auditEntry.source()),
                    nonNull(auditEntry.action()),
                    nonNull(auditEntry.entityType()),
                    nonNull(auditEntry.entityId()),
                    nonNull(auditEntry.details())
            ));
        }

        if (replaceData) {
            updateRepository.replaceAll(updates, weeklyMessages);
        }
        if (replaceConfig && snapshot.config() != null) {
            Map<String, Object> configPayload = new LinkedHashMap<>();
            configPayload.putAll(snapshot.config());
            configService.updateFromMap(configPayload);
        }
        if (replaceAudit) {
            auditRepository.replaceAll(auditEntries);
        }

        return new ImportResult(
                updates.size(),
                weeklyMessages.size(),
                auditEntries.size(),
                replaceData,
                replaceConfig,
                replaceAudit
        );
    }

    public ImportResult importCsv(String csvPayload, String defaultAuthor) {
        List<String> lines = csvPayload == null ? List.of() : csvPayload.lines().toList();
        if (lines.isEmpty()) {
            return new ImportResult(0, 0, 0, false, false, false);
        }

        int created = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank()) {
                continue;
            }
            if (i == 0 && line.toLowerCase(Locale.ROOT).startsWith("id,")) {
                continue;
            }

            List<String> columns = parseCsvLine(line);
            if (columns.size() < 4) {
                continue;
            }
            String weekStartRaw = column(columns, 1);
            String typeRaw = column(columns, 2);
            String content = column(columns, 3);
            String author = column(columns, 4);
            if (content.isBlank()) {
                continue;
            }
            Optional<UpdateType> parsedType = UpdateType.fromKey(typeRaw);
            if (parsedType.isEmpty()) {
                continue;
            }

            LocalDate weekStart;
            try {
                weekStart = LocalDate.parse(weekStartRaw);
            } catch (Exception ex) {
                continue;
            }
            String resolvedAuthor = author.isBlank() ? nonNull(defaultAuthor) : author;
            updateRepository.create(weekStart, parsedType.get(), content, resolvedAuthor);
            created++;
        }

        return new ImportResult(created, 0, 0, false, false, false);
    }

    public BackupResult createBackupFile() {
        BotConfig config = configService.get();
        if (!config.backupEnabled()) {
            throw new IllegalStateException("backup_enabled=false");
        }

        Path directory = backupDirectory(config);
        ensureDirectory(directory);

        String fileName = "backup-" + BACKUP_FILE_FORMAT.format(java.time.LocalDateTime.now()) + ".json";
        Path target = directory.resolve(fileName).normalize();
        String payload = exportJson(config.backupIncludeAudit());

        try {
            Files.writeString(target, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write backup file", ex);
        }

        trimBackups(directory, config.backupMaxFiles());
        return new BackupResult(fileName, target.toString(), Files.exists(target));
    }

    public List<BackupItem> listBackups() {
        BotConfig config = configService.get();
        Path directory = backupDirectory(config);
        if (!Files.exists(directory)) {
            return List.of();
        }
        try {
            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .toList();
            }

            List<BackupItem> result = new ArrayList<>();
            for (Path file : files) {
                result.add(new BackupItem(
                        file.getFileName().toString(),
                        Files.size(file),
                        Files.getLastModifiedTime(file).toInstant().toString()
                ));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to list backups", ex);
        }
    }

    public ImportResult restoreBackup(String fileName, boolean replaceConfig, boolean replaceAudit) {
        BotConfig config = configService.get();
        if (!config.backupEnabled()) {
            throw new IllegalStateException("backup_enabled=false");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Backup file name is required");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid backup file name");
        }

        Path directory = backupDirectory(config);
        Path target = directory.resolve(fileName).normalize();
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Backup file not found: " + fileName);
        }

        String payload;
        try {
            payload = Files.readString(target, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read backup file", ex);
        }
        return importJson(payload, true, replaceConfig, replaceAudit);
    }

    private Snapshot snapshot(boolean includeAudit) {
        List<SnapshotUpdate> updates = updateRepository.findAll().stream()
                .map(entry -> new SnapshotUpdate(
                        entry.id(),
                        entry.weekStart().toString(),
                        entry.type().key(),
                        entry.content(),
                        entry.author(),
                        entry.createdAt().toString(),
                        entry.updatedAt().toString()
                ))
                .toList();
        List<SnapshotWeeklyMessage> weeklyMessages = updateRepository.findAllWeeklyMessages().stream()
                .map(record -> new SnapshotWeeklyMessage(
                        record.weekStart().toString(),
                        record.channelId(),
                        record.messageId(),
                        record.createdAt().toString()
                ))
                .toList();
        List<SnapshotAuditEntry> auditLog = includeAudit
                ? auditRepository.findAll().stream()
                .map(entry -> new SnapshotAuditEntry(
                        entry.id(),
                        entry.createdAt().toString(),
                        entry.actor(),
                        entry.source(),
                        entry.action(),
                        entry.entityType(),
                        entry.entityId(),
                        entry.details()
                ))
                .toList()
                : List.of();

        return new Snapshot(
                "1.0",
                Instant.now().toString(),
                configService.getRawMap(),
                updates,
                weeklyMessages,
                auditLog
        );
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String normalized = value.replace("\r", "").replace("\n", "\\n").replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(normalizeCsvValue(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(normalizeCsvValue(current.toString()));
        return values;
    }

    private String normalizeCsvValue(String value) {
        return value.replace("\\n", "\n").trim();
    }

    private String column(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return "";
        }
        return columns.get(index);
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create backup directory", ex);
        }
    }

    private void trimBackups(Path directory, int maxFiles) {
        try {
            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .toList();
            }
            for (int i = maxFiles; i < files.size(); i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to trim backup files", ex);
        }
    }

    private Path backupDirectory(BotConfig config) {
        return Path.of(config.backupDirectory()).toAbsolutePath().normalize();
    }

    private Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }

    public record ImportResult(
            int importedUpdates,
            int importedWeeklyMessages,
            int importedAuditEntries,
            boolean replacedData,
            boolean replacedConfig,
            boolean replacedAudit
    ) {
    }

    public record BackupResult(
            String fileName,
            String fullPath,
            boolean written
    ) {
    }

    public record BackupItem(
            String fileName,
            long sizeBytes,
            String lastModifiedAt
    ) {
    }

    public record Snapshot(
            String version,
            String exportedAt,
            Map<String, String> config,
            List<SnapshotUpdate> updates,
            List<SnapshotWeeklyMessage> weeklyMessages,
            List<SnapshotAuditEntry> auditLog
    ) {
        public Snapshot {
            config = config == null ? Map.of() : Map.copyOf(config);
            updates = updates == null ? List.of() : List.copyOf(updates);
            weeklyMessages = weeklyMessages == null ? List.of() : List.copyOf(weeklyMessages);
            auditLog = auditLog == null ? List.of() : List.copyOf(auditLog);
        }
    }

    public record SnapshotUpdate(
            long id,
            String weekStart,
            String type,
            String content,
            String author,
            String createdAt,
            String updatedAt
    ) {
    }

    public record SnapshotWeeklyMessage(
            String weekStart,
            String channelId,
            String messageId,
            String createdAt
    ) {
    }

    public record SnapshotAuditEntry(
            long id,
            String createdAt,
            String actor,
            String source,
            String action,
            String entityType,
            String entityId,
            String details
    ) {
    }
}
