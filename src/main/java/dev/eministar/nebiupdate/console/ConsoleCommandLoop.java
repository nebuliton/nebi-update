package dev.eministar.nebiupdate.console;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Scanner;

public final class ConsoleCommandLoop implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommandLoop.class);

    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final WeekService weekService;
    private final DiscordGateway discordGateway;
    private volatile boolean running = true;
    private Thread worker;

    public ConsoleCommandLoop(
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            DiscordGateway discordGateway
    ) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.discordGateway = discordGateway;
    }

    public void start() {
        worker = new Thread(this::runLoop, "console-command-loop");
        worker.setDaemon(true);
        worker.start();
        LOGGER.info("Console command loop started (type 'help')");
    }

    private void runLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (running && scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isBlank()) {
                    continue;
                }
                handleCommand(line);
            }
        } catch (Exception ex) {
            LOGGER.error("Console loop stopped due to error", ex);
        }
    }

    private void handleCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();
        switch (command) {
            case "help" -> printHelp();
            case "status" -> printStatus();
            case "sync" -> {
                discordGateway.requestSyncCurrentWeek(true);
                System.out.println("[console] Sync ausgelöst.");
            }
            case "test" -> {
                discordGateway.requestSendTestCurrentWeek();
                System.out.println("[console] Test-Nachricht wird gesendet.");
            }
            case "config" -> printConfig();
            case "set" -> {
                if (parts.length < 3) {
                    System.out.println("[console] Nutzung: set <key> <value>");
                    return;
                }
                applyConfigUpdate(parts[1], parts[2]);
            }
            case "commands" -> {
                discordGateway.registerSlashCommands();
                System.out.println("[console] Slash-Commands neu registriert.");
            }
            case "exit", "quit" -> {
                System.out.println("[console] Beende Anwendung...");
                System.exit(0);
            }
            default -> System.out.println("[console] Unbekannt: " + command + " (help)");
        }
    }

    private void applyConfigUpdate(String key, String value) {
        try {
            configService.updateFromMap(Map.of(key, value));
            System.out.println("[console] Config aktualisiert: " + key + "=" + value);
        } catch (Exception ex) {
            System.out.println("[console] Config-Update fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void printStatus() {
        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        int count = updateRepository.countByWeek(week.start());
        System.out.println("[status] Woche: " + week.label());
        System.out.println("[status] Updates: " + count);
        System.out.println("[status] Channel: " + config.channelId());
        System.out.println("[status] Bot connected: " + discordGateway.isConnected());
    }

    private void printConfig() {
        Map<String, String> config = configService.getRawMap();
        System.out.println("[config]");
        for (Map.Entry<String, String> entry : config.entrySet()) {
            System.out.println("- " + entry.getKey() + " = " + entry.getValue());
        }
    }

    private void printHelp() {
        System.out.println("=== NebiUpdate Console Commands ===");
        System.out.println("help      -> Diese Hilfe");
        System.out.println("status    -> Wochenstatus anzeigen");
        System.out.println("sync      -> Wochen-Nachricht sofort synchronisieren");
        System.out.println("test      -> Test-Nachricht senden (ohne Wochenpost-Speicherung)");
        System.out.println("config    -> Aktuelle Konfiguration ausgeben");
        System.out.println("set k v   -> Konfigurationswert setzen");
        System.out.println("commands  -> Slash-Commands neu registrieren");
        System.out.println("exit      -> Prozess beenden");
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }
}
