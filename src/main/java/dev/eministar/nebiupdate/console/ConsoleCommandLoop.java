package dev.eministar.nebiupdate.console;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.config.ConfigService;
import dev.eministar.nebiupdate.audit.AuditService;
import dev.eministar.nebiupdate.data.UpdateRepository;
import dev.eministar.nebiupdate.discord.DiscordGateway;
import dev.eministar.nebiupdate.logging.ErrorLogger;
import dev.eministar.nebiupdate.time.WeekService;
import dev.eministar.nebiupdate.time.WeekWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public final class ConsoleCommandLoop implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommandLoop.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GRAY = "\u001B[90m";

    private final ConfigService configService;
    private final UpdateRepository updateRepository;
    private final WeekService weekService;
    private final DiscordGateway discordGateway;
    private final AuditService auditService;
    private final Map<String, CommandSpec> commandIndex = new LinkedHashMap<>();
    private final List<CommandSpec> commandList = new ArrayList<>();
    private final boolean ansiEnabled;

    private volatile boolean running = true;
    private Thread worker;

    public ConsoleCommandLoop(
            ConfigService configService,
            UpdateRepository updateRepository,
            WeekService weekService,
            DiscordGateway discordGateway,
            AuditService auditService
    ) {
        this.configService = configService;
        this.updateRepository = updateRepository;
        this.weekService = weekService;
        this.discordGateway = discordGateway;
        this.auditService = auditService;
        this.ansiEnabled = resolveAnsiEnabled();
        registerCommands();
    }

    public void start() {
        worker = new Thread(this::runLoop, "console-command-loop");
        worker.setDaemon(true);
        worker.start();
        LOGGER.info("Console command loop started");
        printConsoleHeader();
    }

    private void runLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                printPrompt();
                if (!scanner.hasNextLine()) {
                    break;
                }
                String rawLine = scanner.nextLine();
                hideEchoedInputLine(rawLine);
                String line = rawLine.trim();
                if (line.isBlank()) {
                    continue;
                }
                handleCommand(line);
            }
        } catch (Exception ex) {
            ErrorLogger.capture(LOGGER, "CONSOLE", "Console loop stopped due to error", ex);
        }
    }

    private void handleCommand(String line) {
        ParsedInput input = parseInput(line);
        CommandSpec commandSpec = commandIndex.get(input.command());
        if (commandSpec == null) {
            printUnknownCommand(input.command());
            return;
        }

        try {
            commandSpec.handler().execute(input);
        } catch (CommandUsageException ex) {
            printError(ex.getMessage());
            String usage = commandSpec.usage().isBlank()
                    ? commandSpec.name()
                    : commandSpec.name() + " " + commandSpec.usage();
            printInfo("Nutzung: " + usage);
        } catch (Exception ex) {
            String errorId = ErrorLogger.capture(LOGGER, "CONSOLE_CMD", ex, "Console command failed: {}", input.command());
            printError("Befehl fehlgeschlagen. Fehler-ID: " + errorId);
        }
    }

    private void applyConfigUpdate(String key, String value) {
        try {
            configService.updateFromMap(Map.of(key, value));
            auditService.log(
                    "console",
                    "console",
                    "config.update",
                    "config",
                    key,
                    Map.of("value", value)
            );
            printSuccess("Config aktualisiert: " + key + "=" + value);
        } catch (Exception ex) {
            printError("Config-Update fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void printStatus() {
        BotConfig config = configService.get();
        WeekWindow week = weekService.currentWeek(config);
        int count = updateRepository.countByWeek(week.start());
        String scheduleAt = weekService.scheduledDateTime(week, config).format(DATETIME_FORMAT);

        printSection("Status");
        System.out.println("Woche         : " + week.label());
        System.out.println("Updates       : " + count);
        System.out.println("Guild         : " + emptyAs(config.guildId(), "(global)"));
        System.out.println("Channel       : " + emptyAs(config.channelId(), "(nicht gesetzt)"));
        System.out.println("Zeitplan      : " + config.scheduleDay() + " " + config.scheduleTime() + " (" + config.timezone() + ")");
        System.out.println("Nächster Slot : " + scheduleAt);
        System.out.println("Discord       : " + (discordGateway.isConnected() ? "verbunden" : "offline"));
    }

    private void printConfig() {
        Map<String, String> config = configService.getRawMap();
        printSection("Konfiguration");
        for (Map.Entry<String, String> entry : config.entrySet()) {
            System.out.printf("%-24s = %s%n", entry.getKey(), entry.getValue());
        }
    }

    private void printHelp(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            printSection("Verfügbare Befehle");
            System.out.printf("%-12s %-14s %-42s%n", "Befehl", "Alias", "Beschreibung");
            System.out.println("----------------------------------------------------------------------------");
            for (CommandSpec command : commandList) {
                String alias = command.aliases().isEmpty() ? "-" : String.join(", ", command.aliases());
                System.out.printf("%-12s %-14s %-42s%n", command.name(), alias, command.description());
            }
            printInfo("Details: help <befehl>");
            return;
        }

        CommandSpec command = commandIndex.get(commandName.toLowerCase(Locale.ROOT));
        if (command == null) {
            printUnknownCommand(commandName);
            return;
        }

        printSection("Hilfe: " + command.name());
        System.out.println("Beschreibung : " + command.description());
        System.out.println("Alias        : " + (command.aliases().isEmpty() ? "-" : String.join(", ", command.aliases())));
        System.out.println("Nutzung      : " + command.name() + (command.usage().isBlank() ? "" : " " + command.usage()));
    }

    private void registerCommands() {
        register(new CommandSpec(
                "help",
                List.of("h", "?"),
                "[befehl]",
                "Hilfe anzeigen",
                input -> printHelp(input.args().isEmpty() ? "" : input.args().get(0))
        ));
        register(new CommandSpec(
                "status",
                List.of("st"),
                "",
                "Wochenstatus anzeigen",
                input -> printStatus()
        ));
        register(new CommandSpec(
                "sync",
                List.of("s"),
                "",
                "Wochen-Nachricht sofort synchronisieren",
                input -> {
                    discordGateway.requestSyncCurrentWeek(true);
                    auditService.log("console", "console", "weekly.sync", "weekly_message", "current_week", Map.of());
                    printSuccess("Sync ausgelöst.");
                }
        ));
        register(new CommandSpec(
                "test",
                List.of("t"),
                "",
                "Test-Nachricht senden",
                input -> {
                    discordGateway.requestSendTestCurrentWeek();
                    auditService.log("console", "console", "weekly.test", "weekly_message", "current_week", Map.of());
                    printSuccess("Test-Nachricht wird gesendet.");
                }
        ));
        register(new CommandSpec(
                "config",
                List.of("cfg"),
                "",
                "Aktuelle Konfiguration anzeigen",
                input -> printConfig()
        ));
        register(new CommandSpec(
                "set",
                List.of(),
                "<key> <value>",
                "Konfigurationswert setzen",
                input -> {
                    String[] pair = input.rawArgs().split("\\s+", 2);
                    if (pair.length < 2) {
                        throw new CommandUsageException("Fehlende Argumente.");
                    }
                    applyConfigUpdate(pair[0], pair[1]);
                }
        ));
        register(new CommandSpec(
                "commands",
                List.of("slash"),
                "",
                "Slash-Commands neu registrieren",
                input -> {
                    discordGateway.registerSlashCommands();
                    auditService.log("console", "console", "commands.reregister", "discord", "slash_commands", Map.of());
                    printSuccess("Slash-Commands neu registriert.");
                }
        ));
        register(new CommandSpec(
                "exit",
                List.of("quit", "stop"),
                "",
                "Anwendung beenden",
                input -> {
                    printInfo("Beende Anwendung...");
                    System.exit(0);
                }
        ));
    }

    private void register(CommandSpec commandSpec) {
        commandList.add(commandSpec);
        commandIndex.put(commandSpec.name().toLowerCase(Locale.ROOT), commandSpec);
        for (String alias : commandSpec.aliases()) {
            commandIndex.put(alias.toLowerCase(Locale.ROOT), commandSpec);
        }
    }

    private ParsedInput parseInput(String line) {
        String trimmed = line.trim();
        int firstSpace = trimmed.indexOf(' ');
        String command;
        String rawArgs;
        if (firstSpace < 0) {
            command = trimmed.toLowerCase(Locale.ROOT);
            rawArgs = "";
        } else {
            command = trimmed.substring(0, firstSpace).toLowerCase(Locale.ROOT);
            rawArgs = trimmed.substring(firstSpace + 1).trim();
        }
        return new ParsedInput(command, rawArgs, tokenize(rawArgs));
    }

    private List<String> tokenize(String rawArgs) {
        List<String> result = new ArrayList<>();
        if (rawArgs.isBlank()) {
            return result;
        }
        for (String token : rawArgs.split("\\s+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private void printUnknownCommand(String command) {
        printError("Unbekannter Befehl: " + command);
        String suggestion = suggestCommand(command);
        if (suggestion != null) {
            printInfo("Meintest du: " + suggestion + " ?");
        }
        printInfo("Tippe 'help' für alle Befehle.");
    }

    private String suggestCommand(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.toLowerCase(Locale.ROOT);
        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;

        for (CommandSpec command : commandList) {
            int distance = levenshtein(normalized, command.name());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = command.name();
            }
        }

        if (bestMatch == null) {
            return null;
        }
        if (bestDistance <= 2 || bestMatch.startsWith(normalized) || normalized.startsWith(bestMatch)) {
            return bestMatch;
        }
        return null;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private void printConsoleHeader() {
        String headline = style("NebiUpdate Konsole", ANSI_BOLD + ANSI_CYAN);
        String subline = style("Tippe 'help' für Befehle.", ANSI_DIM + ANSI_GRAY);
        printSection(headline);
        System.out.println(subline);
        System.out.println();
    }

    private void printSection(String title) {
        String line = "=".repeat(Math.max(18, title.length() + 6));
        System.out.println(style(line, ANSI_DIM + ANSI_GRAY));
        System.out.println("  " + title);
        System.out.println(style(line, ANSI_DIM + ANSI_GRAY));
    }

    private void printInfo(String message) {
        System.out.println(tag("INFO", ANSI_BLUE) + " " + message);
    }

    private void printSuccess(String message) {
        System.out.println(tag("OK", ANSI_GREEN) + " " + message);
    }

    private void printError(String message) {
        System.out.println(tag("ERR", ANSI_RED) + " " + message);
    }

    private String emptyAs(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private void printPrompt() {
        String prefix = style("[NebiUpdate]", ANSI_BOLD + ANSI_MAGENTA);
        String arrow = style(" >", ANSI_CYAN);
        System.out.print(prefix + arrow + " ");
        System.out.flush();
    }

    private void hideEchoedInputLine(String rawLine) {
        if (!ansiEnabled) {
            return;
        }
        if (rawLine == null) {
            return;
        }
        // Move one line up (entered command) and clear it for a cleaner command UI.
        System.out.print("\u001B[1A\u001B[2K\r");
        System.out.flush();
    }

    private boolean resolveAnsiEnabled() {
        String ansiProperty = System.getProperty("terminal.ansi", "").trim().toLowerCase(Locale.ROOT);
        if (ansiProperty.equals("true") || ansiProperty.equals("1") || ansiProperty.equals("yes") || ansiProperty.equals("on")) {
            return true;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return !os.contains("win");
    }

    private String tag(String label, String color) {
        String padded = "[" + label + "]";
        return style(padded, ANSI_BOLD + color);
    }

    private String style(String text, String color) {
        if (!ansiEnabled) {
            return text;
        }
        return color + text + ANSI_RESET;
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private record ParsedInput(String command, String rawArgs, List<String> args) {
    }

    @FunctionalInterface
    private interface CommandHandler {
        void execute(ParsedInput input) throws Exception;
    }

    private record CommandSpec(
            String name,
            List<String> aliases,
            String usage,
            String description,
            CommandHandler handler
    ) {
    }

    private static final class CommandUsageException extends RuntimeException {
        private CommandUsageException(String message) {
            super(message);
        }
    }
}
