package dev.eministar.nebiupdate.web;

import dev.eministar.nebiupdate.audit.AuditEntry;
import dev.eministar.nebiupdate.audit.AuditService;
import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.data.UpdateType;
import dev.eministar.nebiupdate.data.WeeklyTypeStats;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.discord.WeeklyMessageRenderer;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import dev.eministar.nebiupdate.transfer.DataPortService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.util.JavalinBindException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DashboardServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardServer.class);

    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final WeekService weekService;
    private final WeeklyMessageRenderer renderer;
    private final DiscordGateway discordGateway;
    private final String dashboardToken;
    private final AuditService auditService;
    private final DataPortService dataPortService;

    private final String dashboardHtml;
    private Javalin app;

    public DashboardServer(
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            WeeklyMessageRenderer renderer,
            DiscordGateway discordGateway,
            String dashboardToken,
            AuditService auditService,
            DataPortService dataPortService
    ) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.renderer = renderer;
        this.discordGateway = discordGateway;
        this.dashboardToken = dashboardToken == null ? "" : dashboardToken.trim();
        this.auditService = auditService;
        this.dataPortService = dataPortService;
        this.dashboardHtml = readResource("/dashboard/index.html");
    }

    public void start() {
        BotConfig config = configService.get();

        app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.staticFiles.add("/dashboard", Location.CLASSPATH);
            if (!dashboardToken.isBlank()) {
                javalinConfig.routes.before("/api/*", ctx -> {
                    String provided = Optional.ofNullable(ctx.header("X-Dashboard-Token")).orElse("");
                    if (!dashboardToken.equals(provided)) {
                        ctx.status(HttpStatus.UNAUTHORIZED).result("Unauthorized");
                    }
                });
            }

            javalinConfig.routes.get("/", ctx -> ctx.html(dashboardHtml));
            javalinConfig.routes.get("/health", ctx -> ctx.result("ok"));

            javalinConfig.routes.get("/api/status", ctx -> {
                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("connected", discordGateway.isConnected());
                payload.put("weekLabel", week.label());
                payload.put("weekStart", week.start().toString());
                payload.put("weekEnd", week.end().toString());
                payload.put("updateCount", updateRepository.countByWeek(week.start()));
                payload.put("channelId", current.channelId());
                payload.put("guildId", current.guildId());
                payload.put("scheduleDay", current.scheduleDay());
                payload.put("scheduleTime", current.scheduleTime());
                payload.put("timezone", current.timezone());
                payload.put("locale", current.locale());
                payload.put("fallbackLocale", current.fallbackLocale());
                payload.put("i18nEnabled", current.i18nEnabled());
                payload.put("auditEnabled", current.auditEnabled());
                payload.put("analyticsEnabled", current.analyticsEnabled());
                payload.put("analyticsWeeks", current.analyticsWeeks());
                payload.put("exportImportEnabled", current.exportImportEnabled());
                payload.put("backupEnabled", current.backupEnabled());
                ctx.json(payload);
            });
            javalinConfig.routes.get("/api/config", ctx -> ctx.json(configService.get().toMap()));
            javalinConfig.routes.put("/api/config", ctx -> {
                Map<String, String> before = configService.getRawMap();
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                BotConfig updated = configService.updateFromMap(body);
                discordGateway.registerSlashCommands();
                Map<String, Object> changes = changedConfig(before, updated.toMap());
                if (!changes.isEmpty()) {
                    auditService.log(
                            actorFromContext(ctx, "dashboard"),
                            "dashboard",
                            "config.update",
                            "config",
                            "bot_config",
                            Map.of("changes", changes.toString())
                    );
                }
                ctx.json(updated.toMap());
            });

            javalinConfig.routes.get("/api/updates/current", ctx -> {
                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);
                List<UpdateEntry> entries = updateRepository.findByWeek(week.start());
                ctx.json(entries.stream().map(this::toUpdateMap).toList());
            });

            javalinConfig.routes.post("/api/updates/current", ctx -> {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                String typeRaw = toStringSafe(body.get("type"));
                String text = toStringSafe(body.get("text")).trim();
                if (text.isBlank()) {
                    ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Text ist erforderlich"));
                    return;
                }
                Optional<UpdateType> parsedType = UpdateType.fromKey(typeRaw);
                if (parsedType.isEmpty()) {
                    ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Ungültiger Typ"));
                    return;
                }

                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);
                String author = toStringSafe(body.get("author")).trim();
                if (author.isBlank()) {
                    author = "Dashboard";
                }
                UpdateEntry created = updateRepository.create(week.start(), parsedType.get(), text, author);
                auditService.log(
                        actorFromContext(ctx, author),
                        "dashboard",
                        "update.create",
                        "update",
                        Long.toString(created.id()),
                        Map.of(
                                "weekStart", week.start().toString(),
                                "type", parsedType.get().key()
                        )
                );
                discordGateway.requestSyncCurrentWeek(true);
                ctx.status(HttpStatus.CREATED).json(toUpdateMap(created));
            });

            javalinConfig.routes.put("/api/updates/current/{id}", ctx -> {
                long id = Long.parseLong(ctx.pathParam("id"));
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);

                Optional<UpdateEntry> existingOpt = updateRepository.findByIdInWeek(id, week.start());
                if (existingOpt.isEmpty()) {
                    ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Eintrag nicht gefunden"));
                    return;
                }
                UpdateEntry existing = existingOpt.get();

                String typeRaw = toStringSafe(body.get("type"));
                String textRaw = toStringSafe(body.get("text")).trim();
                String authorRaw = toStringSafe(body.get("author")).trim();

                UpdateType type = existing.type();
                if (!typeRaw.isBlank()) {
                    Optional<UpdateType> parsed = UpdateType.fromKey(typeRaw);
                    if (parsed.isEmpty()) {
                        ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Ungültiger Typ"));
                        return;
                    }
                    type = parsed.get();
                }

                String content = textRaw.isBlank() ? existing.content() : textRaw;
                String author = authorRaw.isBlank() ? "Dashboard" : authorRaw;
                boolean updated = updateRepository.updateInWeek(id, week.start(), type, content, author);
                if (!updated) {
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Aktualisierung fehlgeschlagen"));
                    return;
                }
                auditService.log(
                        actorFromContext(ctx, author),
                        "dashboard",
                        "update.edit",
                        "update",
                        Long.toString(id),
                        Map.of(
                                "weekStart", week.start().toString(),
                                "type", type.key()
                        )
                );
                discordGateway.requestSyncCurrentWeek(true);
                UpdateEntry refreshed = updateRepository.findByIdInWeek(id, week.start()).orElse(existing);
                ctx.json(toUpdateMap(refreshed));
            });

            javalinConfig.routes.delete("/api/updates/current/{id}", ctx -> {
                long id = Long.parseLong(ctx.pathParam("id"));
                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);
                boolean deleted = updateRepository.deleteInWeek(id, week.start());
                if (!deleted) {
                    ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Eintrag nicht gefunden"));
                    return;
                }
                auditService.log(
                        actorFromContext(ctx, "dashboard"),
                        "dashboard",
                        "update.delete",
                        "update",
                        Long.toString(id),
                        Map.of("weekStart", week.start().toString())
                );
                discordGateway.requestSyncCurrentWeek(true);
                ctx.json(Map.of("ok", true));
            });

            javalinConfig.routes.get("/api/preview/current", ctx -> {
                BotConfig current = configService.get();
                WeekWindow week = weekService.currentWeek(current);
                List<UpdateEntry> entries = updateRepository.findByWeek(week.start());
                ctx.result(renderer.renderWeeklyMessage(week, entries, current));
            });

            javalinConfig.routes.post("/api/actions/sync", ctx -> {
                discordGateway.requestSyncCurrentWeek(true);
                auditService.log(actorFromContext(ctx, "dashboard"), "dashboard", "weekly.sync", "weekly_message", "current_week", Map.of());
                ctx.json(Map.of("ok", true));
            });

            javalinConfig.routes.post("/api/actions/test", ctx -> {
                discordGateway.requestSendTestCurrentWeek();
                auditService.log(actorFromContext(ctx, "dashboard"), "dashboard", "weekly.test", "weekly_message", "current_week", Map.of());
                ctx.json(Map.of("ok", true));
            });

            javalinConfig.routes.get("/api/audit", ctx -> {
                BotConfig current = configService.get();
                if (!current.auditEnabled()) {
                    ctx.json(Map.of("enabled", false, "entries", List.of()));
                    return;
                }
                int limit = clamp(parseIntSafe(ctx.queryParam("limit"), 120), 1, 1000);
                List<Map<String, Object>> entries = auditService.recent(limit).stream().map(this::toAuditMap).toList();
                ctx.json(Map.of("enabled", true, "entries", entries));
            });

            javalinConfig.routes.get("/api/analytics", ctx -> {
                BotConfig current = configService.get();
                if (!current.analyticsEnabled()) {
                    ctx.json(Map.of("enabled", false, "weeks", List.of(), "totals", Map.of()));
                    return;
                }
                List<WeeklyTypeStats> weeks = updateRepository.findWeeklyTypeStats(current.analyticsWeeks());
                int totalAdded = 0;
                int totalChanged = 0;
                int totalRemoved = 0;
                for (WeeklyTypeStats stat : weeks) {
                    totalAdded += stat.added();
                    totalChanged += stat.changed();
                    totalRemoved += stat.removed();
                }
                Map<String, Object> totals = new LinkedHashMap<>();
                totals.put("added", totalAdded);
                totals.put("changed", totalChanged);
                totals.put("removed", totalRemoved);
                totals.put("total", totalAdded + totalChanged + totalRemoved);

                List<Map<String, Object>> series = weeks.stream()
                        .map(this::toWeeklyStatsMap)
                        .toList();
                ctx.json(Map.of(
                        "enabled", true,
                        "windowWeeks", current.analyticsWeeks(),
                        "weeks", series,
                        "totals", totals
                ));
            });

            javalinConfig.routes.get("/api/export/json", ctx -> {
                BotConfig current = configService.get();
                if (!current.exportImportEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Export/Import ist deaktiviert"));
                    return;
                }
                boolean includeAudit = boolValue(ctx.queryParam("include_audit"), current.backupIncludeAudit());
                String payload = dataPortService.exportJson(includeAudit);
                ctx.contentType("application/json; charset=utf-8");
                ctx.result(payload);
            });

            javalinConfig.routes.get("/api/export/csv", ctx -> {
                BotConfig current = configService.get();
                if (!current.exportImportEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Export/Import ist deaktiviert"));
                    return;
                }
                String scope = toStringSafe(ctx.queryParam("scope")).trim().toLowerCase();
                List<UpdateEntry> entries;
                if (scope.equals("current")) {
                    WeekWindow week = weekService.currentWeek(current);
                    entries = updateRepository.findByWeek(week.start());
                } else {
                    entries = updateRepository.findAll();
                }
                String csv = dataPortService.exportCsv(entries);
                ctx.header("Content-Disposition", "attachment; filename=\"nebiupdate-updates.csv\"");
                ctx.contentType("text/csv; charset=utf-8");
                ctx.result(csv);
            });

            javalinConfig.routes.post("/api/import/json", ctx -> {
                BotConfig current = configService.get();
                if (!current.exportImportEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Export/Import ist deaktiviert"));
                    return;
                }
                boolean replaceData = boolValue(ctx.queryParam("replace_data"), true);
                boolean replaceConfig = boolValue(ctx.queryParam("replace_config"), false);
                boolean replaceAudit = boolValue(ctx.queryParam("replace_audit"), current.backupIncludeAudit());
                DataPortService.ImportResult result = dataPortService.importJson(
                        ctx.body(),
                        replaceData,
                        replaceConfig,
                        replaceAudit
                );
                auditService.log(
                        actorFromContext(ctx, "dashboard"),
                        "dashboard",
                        "import.json",
                        "data_port",
                        "json",
                        Map.of(
                                "replaceData", replaceData,
                                "replaceConfig", replaceConfig,
                                "replaceAudit", replaceAudit
                        )
                );
                discordGateway.requestSyncCurrentWeek(true);
                ctx.json(toImportMap(result));
            });

            javalinConfig.routes.post("/api/import/csv", ctx -> {
                BotConfig current = configService.get();
                if (!current.exportImportEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Export/Import ist deaktiviert"));
                    return;
                }
                String defaultAuthor = actorFromContext(ctx, "Dashboard");
                DataPortService.ImportResult result = dataPortService.importCsv(ctx.body(), defaultAuthor);
                auditService.log(
                        defaultAuthor,
                        "dashboard",
                        "import.csv",
                        "data_port",
                        "csv",
                        Map.of("importedUpdates", result.importedUpdates())
                );
                discordGateway.requestSyncCurrentWeek(true);
                ctx.json(toImportMap(result));
            });

            javalinConfig.routes.get("/api/backups", ctx -> {
                BotConfig current = configService.get();
                if (!current.backupEnabled()) {
                    ctx.json(Map.of("enabled", false, "items", List.of()));
                    return;
                }
                ctx.json(Map.of(
                        "enabled", true,
                        "items", dataPortService.listBackups()
                ));
            });

            javalinConfig.routes.post("/api/actions/backup", ctx -> {
                BotConfig current = configService.get();
                if (!current.backupEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Backups sind deaktiviert"));
                    return;
                }
                DataPortService.BackupResult backup = dataPortService.createBackupFile();
                auditService.log(
                        actorFromContext(ctx, "dashboard"),
                        "dashboard",
                        "backup.create",
                        "backup",
                        backup.fileName(),
                        Map.of("path", backup.fullPath())
                );
                ctx.json(Map.of(
                        "ok", true,
                        "fileName", backup.fileName(),
                        "path", backup.fullPath()
                ));
            });

            javalinConfig.routes.post("/api/actions/restore", ctx -> {
                BotConfig current = configService.get();
                if (!current.backupEnabled()) {
                    ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "Backups sind deaktiviert"));
                    return;
                }
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                String fileName = toStringSafe(body.get("file"));
                boolean replaceConfig = boolValue(body.get("replaceConfig"), false);
                boolean replaceAudit = boolValue(body.get("replaceAudit"), current.backupIncludeAudit());
                DataPortService.ImportResult restored = dataPortService.restoreBackup(fileName, replaceConfig, replaceAudit);
                auditService.log(
                        actorFromContext(ctx, "dashboard"),
                        "dashboard",
                        "backup.restore",
                        "backup",
                        fileName,
                        Map.of(
                                "replaceConfig", replaceConfig,
                                "replaceAudit", replaceAudit
                        )
                );
                discordGateway.requestSyncCurrentWeek(true);
                ctx.json(toImportMap(restored));
            });

            javalinConfig.routes.exception(Exception.class, (ex, ctx) -> {
                String errorId = ErrorLogger.capture(LOGGER, "DASHBOARD_API", "Dashboard request failed", ex);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of(
                        "error", "Interner Serverfehler",
                        "error_id", errorId
                ));
            });
        });

        try {
            app.start(config.dashboardHost(), config.dashboardPort());
            LOGGER.info("Dashboard started on {}:{}", config.dashboardHost(), config.dashboardPort());
        } catch (JavalinBindException ex) {
            if (hasCannotAssignRequestedAddress(ex)) {
                String message = "Dashboard-Bind fehlgeschlagen: Host '%s' ist auf diesem Server nicht lokal verfügbar. " +
                        "Setze in der config.yml dashboard.host auf '0.0.0.0' (extern erreichbar) oder '127.0.0.1' (nur lokal)."
                        .formatted(config.dashboardHost());
                throw new IllegalStateException(message, ex);
            }
            throw ex;
        }
    }

    private Map<String, Object> toUpdateMap(UpdateEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.id());
        map.put("weekStart", entry.weekStart().toString());
        map.put("type", entry.type().key());
        map.put("content", entry.content());
        map.put("author", entry.author());
        map.put("createdAt", entry.createdAt().toString());
        map.put("updatedAt", entry.updatedAt().toString());
        return map;
    }

    private Map<String, Object> toAuditMap(AuditEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.id());
        map.put("createdAt", entry.createdAt().toString());
        map.put("actor", entry.actor());
        map.put("source", entry.source());
        map.put("action", entry.action());
        map.put("entityType", entry.entityType());
        map.put("entityId", entry.entityId());
        map.put("details", entry.details());
        return map;
    }

    private Map<String, Object> toWeeklyStatsMap(WeeklyTypeStats stats) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("weekStart", stats.weekStart().toString());
        map.put("added", stats.added());
        map.put("changed", stats.changed());
        map.put("removed", stats.removed());
        map.put("total", stats.total());
        return map;
    }

    private Map<String, Object> toImportMap(DataPortService.ImportResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("importedUpdates", result.importedUpdates());
        map.put("importedWeeklyMessages", result.importedWeeklyMessages());
        map.put("importedAuditEntries", result.importedAuditEntries());
        map.put("replacedData", result.replacedData());
        map.put("replacedConfig", result.replacedConfig());
        map.put("replacedAudit", result.replacedAudit());
        return map;
    }

    private Map<String, Object> changedConfig(Map<String, String> before, Map<String, String> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String key = entry.getKey();
            String oldValue = before.getOrDefault(key, "");
            String newValue = entry.getValue();
            if (!oldValue.equals(newValue)) {
                changes.put(key, oldValue + " -> " + newValue);
            }
        }
        return changes;
    }

    private String toStringSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private boolean boolValue(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim().toLowerCase();
        if (value.isBlank()) {
            return fallback;
        }
        return value.equals("true")
                || value.equals("1")
                || value.equals("yes")
                || value.equals("ja")
                || value.equals("on");
    }

    private String actorFromContext(Context ctx, String fallback) {
        String actor = Optional.ofNullable(ctx.header("X-Actor")).orElse("").trim();
        if (!actor.isBlank()) {
            return actor;
        }
        if (fallback == null || fallback.isBlank()) {
            return "Dashboard";
        }
        return fallback;
    }

    private String readResource(String path) {
        try (InputStream inputStream = DashboardServer.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load resource: " + path, ex);
        }
    }

    private boolean hasCannotAssignRequestedAddress(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("cannot assign requested address")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public void close() {
        if (app != null) {
            try {
                app.stop();
            } catch (Throwable ex) {
                String message = "Dashboard stop failed: %s: %s"
                        .formatted(ex.getClass().getSimpleName(), ex.getMessage());
                try {
                    LOGGER.warn(message);
                } catch (Throwable ignored) {
                    // Ignore logging backend failures during shutdown.
                }
                System.err.println(message);
            }
        }
    }
}
