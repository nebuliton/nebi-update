package dev.eministar.nebiupdate.web;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.data.UpdateType;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.discord.WeeklyMessageRenderer;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import io.javalin.Javalin;
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

    private final String dashboardHtml;
    private Javalin app;

    public DashboardServer(
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            WeeklyMessageRenderer renderer,
            DiscordGateway discordGateway,
            String dashboardToken
    ) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.renderer = renderer;
        this.discordGateway = discordGateway;
        this.dashboardToken = dashboardToken == null ? "" : dashboardToken.trim();
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
                ctx.json(payload);
            });
            javalinConfig.routes.get("/api/config", ctx -> ctx.json(configService.get().toMap()));
            javalinConfig.routes.put("/api/config", ctx -> {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                BotConfig updated = configService.updateFromMap(body);
                discordGateway.registerSlashCommands();
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
                ctx.json(Map.of("ok", true));
            });

            javalinConfig.routes.post("/api/actions/test", ctx -> {
                discordGateway.requestSendTestCurrentWeek();
                ctx.json(Map.of("ok", true));
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

    private String toStringSafe(Object value) {
        return value == null ? "" : value.toString();
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
