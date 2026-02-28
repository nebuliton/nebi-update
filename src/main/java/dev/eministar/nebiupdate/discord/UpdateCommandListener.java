package dev.eministar.nebiupdate.discord;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.data.UpdateType;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public final class UpdateCommandListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCommandListener.class);
    private static final int MAX_CONTENT_LENGTH = 900;

    private final DiscordGateway discordGateway;
    private final ConfigService configService;
    private final WeekService weekService;
    private final UpdateRepository updateRepository;
    private final WeeklyMessageRenderer renderer;
    private final ExecutorService commandWorker;

    public UpdateCommandListener(
            DiscordGateway discordGateway,
            ConfigService configService,
            WeekService weekService,
            UpdateRepository updateRepository,
            WeeklyMessageRenderer renderer,
            ExecutorService commandWorker
    ) {
        this.discordGateway = discordGateway;
        this.configService = configService;
        this.weekService = weekService;
        this.updateRepository = updateRepository;
        this.renderer = renderer;
        this.commandWorker = commandWorker;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"update".equals(event.getName())) {
            return;
        }
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Unbekannter Subcommand.").setEphemeral(true).queue(
                    null,
                    failure -> LOGGER.warn("Konnte Antwort für unbekannten Subcommand nicht senden", failure)
            );
            return;
        }

        switch (subcommand) {
            case "add" -> handleDeferred(event, () -> onAdd(event));
            case "edit" -> handleDeferred(event, () -> onEdit(event));
            case "remove" -> handleDeferred(event, () -> onRemove(event));
            case "list" -> handleDeferred(event, () -> onList(event));
            case "sync" -> handleDeferred(event, this::onSync);
            case "test" -> handleDeferred(event, this::onTest);
            default -> event.reply("Unbekannter Subcommand.").setEphemeral(true).queue(
                    null,
                    failure -> LOGGER.warn("Konnte Antwort für Subcommand {} nicht senden", subcommand, failure)
            );
        }
    }

    private String onAdd(SlashCommandInteractionEvent event) {
        OptionMapping typeOption = event.getOption("type");
        OptionMapping textOption = event.getOption("text");
        if (typeOption == null || textOption == null) {
            return "Typ und Text sind erforderlich.";
        }

        String text = sanitizeText(textOption.getAsString());
        if (text.isBlank()) {
            return "Text darf nicht leer sein.";
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            return "Text ist zu lang (max " + MAX_CONTENT_LENGTH + " Zeichen).";
        }

        Optional<UpdateType> parsedType = UpdateType.fromKey(typeOption.getAsString());
        if (parsedType.isEmpty()) {
            return "Ungültiger Typ.";
        }

        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        String author = authorFromEvent(event);
        UpdateEntry created = updateRepository.create(week.start(), parsedType.get(), text, author);
        discordGateway.requestSyncCurrentWeek(true);
        return "✅ Eintrag `#" + created.id() + "` gespeichert und Wochenpost synchronisiert.";
    }

    private String onEdit(SlashCommandInteractionEvent event) {
        OptionMapping idOption = event.getOption("id");
        if (idOption == null) {
            return "ID ist erforderlich.";
        }
        long id = idOption.getAsLong();

        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        Optional<UpdateEntry> existingOptional = updateRepository.findByIdInWeek(id, week.start());
        if (existingOptional.isEmpty()) {
            return "Kein Eintrag mit ID `" + id + "` in der aktuellen Woche gefunden.";
        }

        UpdateEntry existing = existingOptional.get();
        OptionMapping typeOption = event.getOption("type");
        OptionMapping textOption = event.getOption("text");
        UpdateType targetType = existing.type();
        String targetText = existing.content();

        if (typeOption != null) {
            Optional<UpdateType> parsedType = UpdateType.fromKey(typeOption.getAsString());
            if (parsedType.isEmpty()) {
                return "Ungültiger Typ.";
            }
            targetType = parsedType.get();
        }

        if (textOption != null) {
            String sanitized = sanitizeText(textOption.getAsString());
            if (sanitized.isBlank()) {
                return "Text darf nicht leer sein.";
            }
            if (sanitized.length() > MAX_CONTENT_LENGTH) {
                return "Text ist zu lang (max " + MAX_CONTENT_LENGTH + " Zeichen).";
            }
            targetText = sanitized;
        }

        boolean updated = updateRepository.updateInWeek(id, week.start(), targetType, targetText, authorFromEvent(event));
        if (!updated) {
            return "Eintrag konnte nicht aktualisiert werden.";
        }

        discordGateway.requestSyncCurrentWeek(true);
        return "✏️ Eintrag `#" + id + "` aktualisiert.";
    }

    private String onRemove(SlashCommandInteractionEvent event) {
        OptionMapping idOption = event.getOption("id");
        if (idOption == null) {
            return "ID ist erforderlich.";
        }
        long id = idOption.getAsLong();

        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        boolean deleted = updateRepository.deleteInWeek(id, week.start());
        if (!deleted) {
            return "Kein Eintrag mit ID `" + id + "` in der aktuellen Woche gefunden.";
        }

        discordGateway.requestSyncCurrentWeek(true);
        return "🗑️ Eintrag `#" + id + "` entfernt.";
    }

    private String onList(SlashCommandInteractionEvent event) {
        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        List<UpdateEntry> entries = updateRepository.findByWeek(week.start());
        String listMessage = renderer.renderUpdateList(week, entries, config);
        return truncate(listMessage, 1800);
    }

    private String onSync() {
        discordGateway.requestSyncCurrentWeek(true);
        return "🔄 Wochen-Nachricht wird jetzt synchronisiert.";
    }

    private String onTest() {
        discordGateway.requestSendTestCurrentWeek();
        return "🧪 Test-Nachricht wird jetzt gesendet (ohne Wochenpost-Speicherung).";
    }

    private String sanitizeText(String text) {
        return text == null ? "" : text.trim().replace("\r", "");
    }

    private String authorFromEvent(SlashCommandInteractionEvent event) {
        if (event.getUser() == null) {
            return "Discord-User";
        }
        String userId = event.getUser().getId();
        if (userId == null || userId.isBlank()) {
            return "Discord-User";
        }
        return userId.trim();
    }

    private String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    private void handleDeferred(SlashCommandInteractionEvent event, CommandAction action) {
        String subcommand = event.getSubcommandName();
        event.deferReply(true).queue(
                hook -> runDeferredAction(hook, subcommand, action),
                failure -> LOGGER.warn("Konnte /update {} nicht rechtzeitig bestätigen", subcommand, failure)
        );
    }

    private void runDeferredAction(InteractionHook hook, String subcommand, CommandAction action) {
        try {
            commandWorker.submit(() -> sendDeferredResponse(hook, subcommand, action));
        } catch (RejectedExecutionException ex) {
            LOGGER.warn("Command-Worker ist ausgelastet, /update {} wird verworfen", subcommand, ex);
            hook.sendMessage("❌ Der Bot ist gerade ausgelastet. Bitte versuche es erneut.")
                    .queue(
                            null,
                            failure -> LOGGER.warn("Konnte Auslastungs-Meldung für /update {} nicht senden", subcommand, failure)
                    );
        }
    }

    private void sendDeferredResponse(InteractionHook hook, String subcommand, CommandAction action) {
        String response;
        try {
            response = action.execute();
        } catch (Exception ex) {
            String errorId = ErrorLogger.capture(LOGGER, "UPDATE_CMD", ex, "Fehler bei /update {}", subcommand);
            response = "❌ Interner Fehler beim Verarbeiten des Befehls. Fehler-ID: `" + errorId + "`";
        }

        hook.sendMessage(truncate(response, 1800)).queue(
                null,
                failure -> LOGGER.warn("Konnte Antwort für /update {} nicht senden", subcommand, failure)
        );
    }

    @FunctionalInterface
    private interface CommandAction {
        String execute();
    }
}
