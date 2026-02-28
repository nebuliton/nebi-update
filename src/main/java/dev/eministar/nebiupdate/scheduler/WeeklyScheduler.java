package dev.eministar.nebiupdate.scheduler;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class WeeklyScheduler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeeklyScheduler.class);

    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final WeekService weekService;
    private final DiscordGateway discordGateway;
    private final ScheduledExecutorService scheduler;

    public WeeklyScheduler(
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            DiscordGateway discordGateway
    ) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.discordGateway = discordGateway;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "weekly-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 5, 30, TimeUnit.SECONDS);
        LOGGER.info("Weekly scheduler started");
    }

    private void tick() {
        try {
            if (!discordGateway.isConnected()) {
                return;
            }
            BotConfig config = configService.get();
            WeekWindow week = weekService.currentWeek(config);
            if (!weekService.isScheduleReached(week, config)) {
                return;
            }

            if (updateRepository.findWeeklyMessageId(week.start()).isPresent()) {
                return;
            }

            LOGGER.info("Schedule reached for week {}, creating initial weekly message", week.start());
            discordGateway.requestSyncCurrentWeek(false);
        } catch (Exception ex) {
            ErrorLogger.capture(LOGGER, "SCHEDULER", "Scheduler tick failed", ex);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
