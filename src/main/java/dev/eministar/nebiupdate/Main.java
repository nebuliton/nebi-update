package dev.eministar.nebiupdate;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.config.StartupSettings;
import dev.eministar.nebiupdate.config.YamlConfigManager;
import dev.eministar.nebiupdate.console.ConsoleCommandLoop;
import dev.eministar.nebiupdate.data.Database;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.discord.WeeklyMessageRenderer;
import dev.eministar.nebiupdate.scheduler.WeeklyScheduler;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.web.DashboardServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        printBootScreen();

        String configPath = "config.yml";
        YamlConfigManager yamlConfigManager = new YamlConfigManager(Path.of(configPath));
        StartupSettings startupSettings = yamlConfigManager.loadSettings();

        String token = startupSettings.discordToken();
        if (token.isBlank()) {
            throw new IllegalStateException("Discord-Token fehlt. Bitte trage discord.token in config.yml ein.");
        }

        String dbPath = startupSettings.dbPath();
        String dashboardToken = startupSettings.dashboardToken();

        Database database = new Database(dbPath);
        database.initialize();

        ConfigService configService = new ConfigService(database, yamlConfigManager::saveBotConfig);
        BotConfig config = configService.initialize(startupSettings.botConfig());
        UpdateRepository updateRepository = new UpdateRepository(database);
        WeekService weekService = new WeekService();
        WeeklyMessageRenderer renderer = new WeeklyMessageRenderer();

        DiscordGateway discordGateway = new DiscordGateway(token, configService, updateRepository, weekService, renderer);
        WeeklyScheduler scheduler = new WeeklyScheduler(configService, updateRepository, weekService, discordGateway);
        DashboardServer dashboardServer = new DashboardServer(
                configService,
                updateRepository,
                weekService,
                renderer,
                discordGateway,
                dashboardToken
        );
        ConsoleCommandLoop consoleLoop = new ConsoleCommandLoop(configService, updateRepository, weekService, discordGateway);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received");
            closeQuietly(consoleLoop);
            closeQuietly(scheduler);
            closeQuietly(dashboardServer);
            closeQuietly(discordGateway);
            closeQuietly(database);
        }, "shutdown-hook"));

        discordGateway.start();
        scheduler.start();
        dashboardServer.start();
        consoleLoop.start();
        discordGateway.requestSyncCurrentWeek(false);

        LOGGER.info("NebiUpdate is running | Dashboard: http://{}:{}/", config.dashboardHost(), config.dashboardPort());
        new CountDownLatch(1).await();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            LOGGER.warn("Failed to close component {}", closeable.getClass().getSimpleName(), ex);
        }
    }

    private static void printBootScreen() {
        System.out.println("""
                =====================================================
                               NEBIUPDATE
                   Discord Weekly Update Bot + Dashboard + SQLite
                =====================================================
                """);
    }
}
