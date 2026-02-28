package dev.eministar.nebiupdate.discord;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiscordGateway implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordGateway.class);

    private final String token;
    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final WeekService weekService;
    private final WeeklyMessageRenderer renderer;
    private final ExecutorService worker;
    private final ExecutorService commandWorker;

    private JDA jda;

    public DiscordGateway(
            String token,
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            WeeklyMessageRenderer renderer
    ) {
        this.token = token;
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.renderer = renderer;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "discord-sync-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.commandWorker = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "discord-command-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() throws InterruptedException {
        jda = JDABuilder.createDefault(token)
                .addEventListeners(new UpdateCommandListener(this, configService, weekService, updateRepository, renderer, commandWorker))
                .build();
        jda.awaitReady();
        registerSlashCommands();
        LOGGER.info("Discord bot connected as {}", jda.getSelfUser().getAsTag());
    }

    public void registerSlashCommands() {
        if (jda == null) {
            return;
        }
        CommandData command = buildUpdateCommand();
        BotConfig config = configService.get();
        if (!config.guildId().isBlank()) {
            Guild guild = jda.getGuildById(config.guildId());
            if (guild != null) {
                guild.updateCommands().addCommands(command).complete();
                LOGGER.info("Registered /update command in guild {}", guild.getName());
                return;
            }
            LOGGER.warn("Configured guild_id {} not found. Falling back to global command registration.", config.guildId());
        }
        jda.updateCommands().addCommands(command).complete();
        LOGGER.info("Registered /update command globally");
    }

    private CommandData buildUpdateCommand() {
        OptionData requiredType = typeOption(true);
        OptionData optionalType = typeOption(false);

        SubcommandData add = new SubcommandData("add", "🟢 Eintrag zur aktuellen Woche hinzufügen")
                .addOptions(requiredType)
                .addOption(OptionType.STRING, "text", "📝 Inhalt des Changelog-Eintrags", true);

        SubcommandData edit = new SubcommandData("edit", "🟠 Eintrag der aktuellen Woche bearbeiten")
                .addOption(OptionType.INTEGER, "id", "🔢 ID des Eintrags", true)
                .addOptions(optionalType)
                .addOption(OptionType.STRING, "text", "📝 Neuer Text", false);

        SubcommandData remove = new SubcommandData("remove", "🔴 Eintrag der aktuellen Woche löschen")
                .addOption(OptionType.INTEGER, "id", "🔢 ID des Eintrags", true);

        SubcommandData list = new SubcommandData("list", "📋 Einträge der aktuellen Woche anzeigen");

        SubcommandData sync = new SubcommandData("sync", "🔄 Wochen-Nachricht sofort synchronisieren");
        SubcommandData test = new SubcommandData("test", "🧪 Test-Nachricht senden (ohne Wochenpost zu speichern)");

        return Commands.slash("update", "🎮 Wochenupdates verwalten")
                .addSubcommands(add, edit, remove, list, sync, test);
    }

    private OptionData typeOption(boolean required) {
        return new OptionData(OptionType.STRING, "type", "🧭 Art der Änderung", required)
                .addChoice("🟢 Neu", "added")
                .addChoice("🟠 Verändert", "changed")
                .addChoice("🔴 Entfernt", "removed");
    }

    public void requestSyncCurrentWeek(boolean forceCreate) {
        worker.submit(() -> syncCurrentWeek(forceCreate));
    }

    public void requestSendTestCurrentWeek() {
        worker.submit(this::sendTestCurrentWeek);
    }

    public synchronized void sendTestCurrentWeek() {
        if (jda == null) {
            return;
        }
        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        if (config.channelId().isBlank()) {
            LOGGER.warn("channel_id is empty. Skip test send.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(config.channelId());
        if (channel == null) {
            LOGGER.warn("Configured channel_id {} not found.", config.channelId());
            return;
        }

        List<UpdateEntry> entries = updateRepository.findByWeek(week.start());
        List<net.dv8tion.jda.api.components.MessageTopLevelComponent> components = renderer.renderTestContainers(week, entries, config);
        try {
            Message message = channel.sendMessageComponents(components)
                    .useComponentsV2()
                    .setSuppressEmbeds(true)
                    .complete();
            LOGGER.info("Sent test weekly message {} for {}", message.getId(), week.start());
        } catch (Exception ex) {
            ErrorLogger.capture(LOGGER, "DISCORD_TEST", ex, "Failed to send test weekly message for {}", week.start());
        }
    }

    public synchronized void syncCurrentWeek(boolean forceCreate) {
        if (jda == null) {
            return;
        }
        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        if (config.channelId().isBlank()) {
            LOGGER.warn("channel_id is empty. Skip weekly sync.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(config.channelId());
        if (channel == null) {
            LOGGER.warn("Configured channel_id {} not found.", config.channelId());
            return;
        }

        List<UpdateEntry> entries = updateRepository.findByWeek(week.start());
        List<net.dv8tion.jda.api.components.MessageTopLevelComponent> components = renderer.renderWeeklyContainers(week, entries, config);
        Optional<String> existingMessageId = updateRepository.findWeeklyMessageId(week.start());

        if (existingMessageId.isPresent()) {
            String messageId = existingMessageId.get();
            try {
                Message existing = channel.retrieveMessageById(messageId).complete();
                existing.editMessage("")
                        .setEmbeds(List.of())
                        .setComponents(components)
                        .useComponentsV2()
                        .setSuppressEmbeds(true)
                        .complete();
                LOGGER.info("Updated weekly message {} for {}", messageId, week.start());
                return;
            } catch (ErrorResponseException ex) {
                if (ex.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) {
                    ErrorLogger.capture(LOGGER, "DISCORD_SYNC", ex, "Failed to edit weekly message {}", messageId);
                    return;
                }
                LOGGER.warn("Stored weekly message {} no longer exists, creating a new one", messageId);
            } catch (Exception ex) {
                ErrorLogger.capture(LOGGER, "DISCORD_SYNC", ex, "Failed to edit weekly message {}", messageId);
                return;
            }
        }

        boolean shouldCreate = forceCreate || weekService.isScheduleReached(week, config);
        if (!shouldCreate) {
            LOGGER.info("Weekly message for {} not created yet (schedule not reached)", week.start());
            return;
        }

        try {
            Message created = channel.sendMessageComponents(components)
                    .useComponentsV2()
                    .setSuppressEmbeds(true)
                    .complete();
            updateRepository.upsertWeeklyMessage(week.start(), channel.getId(), created.getId());
            LOGGER.info("Created weekly message {} for {}", created.getId(), week.start());
        } catch (Exception ex) {
            ErrorLogger.capture(LOGGER, "DISCORD_SYNC", ex, "Failed to create weekly message for {}", week.start());
        }
    }

    public synchronized boolean isConnected() {
        return jda != null && jda.getStatus() != JDA.Status.SHUTDOWN;
    }

    @Override
    public void close() {
        worker.shutdownNow();
        commandWorker.shutdownNow();
        if (jda != null) {
            jda.shutdown();
        }
    }
}
