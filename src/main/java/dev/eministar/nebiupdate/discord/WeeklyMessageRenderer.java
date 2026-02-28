package dev.eministar.nebiupdate.discord;

import dev.eministar.nebiupdate.config.BotConfig;
import dev.eministar.nebiupdate.data.UpdateEntry;
import dev.eministar.nebiupdate.data.UpdateType;
import dev.eministar.nebiupdate.time.WeekWindow;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeeklyMessageRenderer {
    private static final int TEXT_LIMIT = 1900;
    private static final int CONTAINER_TEXT_LIMIT = 3500;
    private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("^<a?:[\\w-]+:\\d+>$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^\\d{15,25}$");
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("^<@!?(\\d{15,25})>$");

    public List<MessageTopLevelComponent> renderWeeklyContainers(WeekWindow week, List<UpdateEntry> entries, BotConfig config) {
        return buildContainerMessage(week, entries, config, false);
    }

    public List<MessageTopLevelComponent> renderTestContainers(WeekWindow week, List<UpdateEntry> entries, BotConfig config) {
        return buildContainerMessage(week, entries, config, true);
    }

    public String renderWeeklyMessage(WeekWindow week, List<UpdateEntry> entries, BotConfig config) {
        String addedEmoji = resolveEmoji(config.addedEmoji(), config.addedEmojiId(), config.addedEmojiAnimated(), "added");
        String changedEmoji = resolveEmoji(config.changedEmoji(), config.changedEmojiId(), config.changedEmojiAnimated(), "changed");
        String removedEmoji = resolveEmoji(config.removedEmoji(), config.removedEmojiId(), config.removedEmojiAnimated(), "removed");

        StringBuilder builder = new StringBuilder();
        builder.append(resolveEmoji(config.titleEmoji(), config.titleEmojiId(), config.titleEmojiAnimated(), "title"))
                .append(" ").append(config.titleText())
                .append(" [").append(week.label()).append("]\n\n");
        builder.append(addedEmoji)
                .append(" = Neu, ")
                .append(changedEmoji)
                .append(" = Geändert, ")
                .append(removedEmoji)
                .append(" = Entfernt\n\n");
        builder.append(resolveEmoji(config.noticeEmoji(), config.noticeEmojiId(), config.noticeEmojiAnimated(), "notice"))
                .append(" ").append(config.noticeText())
                .append("\n\n");

        int remaining = TEXT_LIMIT - builder.length();
        if (remaining <= 0) {
            return truncate(builder.toString(), TEXT_LIMIT);
        }
        builder.append(buildOrderedChangeLines(entries, config, addedEmoji, changedEmoji, removedEmoji, remaining));
        return truncate(builder.toString(), TEXT_LIMIT);
    }

    public String renderUpdateList(WeekWindow week, List<UpdateEntry> entries, BotConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append("Aktuelle Woche [").append(week.label()).append("]\n");
        if (entries.isEmpty()) {
            builder.append("- Keine Einträge vorhanden.");
            return builder.toString();
        }

        String addedEmoji = resolveEmoji(config.addedEmoji(), config.addedEmojiId(), config.addedEmojiAnimated(), "added");
        String changedEmoji = resolveEmoji(config.changedEmoji(), config.changedEmojiId(), config.changedEmojiAnimated(), "changed");
        String removedEmoji = resolveEmoji(config.removedEmoji(), config.removedEmojiId(), config.removedEmojiAnimated(), "removed");

        int remaining = TEXT_LIMIT - builder.length();
        if (remaining > 0) {
            builder.append(buildOrderedChangeLines(entries, config, addedEmoji, changedEmoji, removedEmoji, remaining));
        }
        return truncate(builder.toString(), TEXT_LIMIT);
    }

    private List<MessageTopLevelComponent> buildContainerMessage(WeekWindow week, List<UpdateEntry> entries, BotConfig config, boolean testMode) {
        String titleEmoji = resolveEmoji(config.titleEmoji(), config.titleEmojiId(), config.titleEmojiAnimated(), "title");
        String addedEmoji = resolveEmoji(config.addedEmoji(), config.addedEmojiId(), config.addedEmojiAnimated(), "added");
        String changedEmoji = resolveEmoji(config.changedEmoji(), config.changedEmojiId(), config.changedEmojiAnimated(), "changed");
        String removedEmoji = resolveEmoji(config.removedEmoji(), config.removedEmojiId(), config.removedEmojiAnimated(), "removed");
        String noticeEmoji = resolveEmoji(config.noticeEmoji(), config.noticeEmojiId(), config.noticeEmojiAnimated(), "notice");

        String headlinePrefix = testMode ? "## 🧪 TEST • " : "## ";
        String headline = headlinePrefix + titleEmoji + " " + config.titleText() + " [" + week.label() + "]";

        String legend = addedEmoji + " Neu • "
                + changedEmoji + " Geändert • "
                + removedEmoji + " Entfernt";

        List<UpdateEntry> addedEntries = entriesOfType(entries, UpdateType.ADDED);
        List<UpdateEntry> changedEntries = entriesOfType(entries, UpdateType.CHANGED);
        List<UpdateEntry> removedEntries = entriesOfType(entries, UpdateType.REMOVED);

        String topBlock = headline + "\n" + legend + "\n\n> " + noticeEmoji + " " + config.noticeText();
        if (testMode) {
            topBlock += "\n> Diese Nachricht ist ein Test und wird nicht als Wochenpost gespeichert.";
        }

        String addedBlock = buildContainerCategoryBlock("Neu", addedEmoji, addedEntries);
        String changedBlock = buildContainerCategoryBlock("Geändert", changedEmoji, changedEntries);
        String removedBlock = buildContainerCategoryBlock("Entfernt", removedEmoji, removedEntries);

        Container container = Container.of(
                TextDisplay.of(truncate(topBlock, CONTAINER_TEXT_LIMIT)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(truncate(addedBlock, CONTAINER_TEXT_LIMIT)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(truncate(changedBlock, CONTAINER_TEXT_LIMIT)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(truncate(removedBlock, CONTAINER_TEXT_LIMIT))
        ).withAccentColor(testMode ? new Color(255, 190, 84) : new Color(116, 163, 255));

        List<MessageTopLevelComponent> components = new ArrayList<>(1);
        components.add(container);
        return components;
    }

    private String buildOrderedChangeLines(
            List<UpdateEntry> entries,
            BotConfig config,
            String addedEmoji,
            String changedEmoji,
            String removedEmoji,
            int maxLength
    ) {
        if (entries.isEmpty()) {
            return "> " + config.noChangeText();
        }

        List<UpdateEntry> addedEntries = entriesOfType(entries, UpdateType.ADDED);
        List<UpdateEntry> changedEntries = entriesOfType(entries, UpdateType.CHANGED);
        List<UpdateEntry> removedEntries = entriesOfType(entries, UpdateType.REMOVED);

        StringBuilder builder = new StringBuilder();
        if (!appendCategoryBlock(builder, "Neu", addedEmoji, addedEntries, maxLength, builder.length() > 0)) {
            return builder.toString().trim();
        }
        if (!appendCategoryBlock(builder, "Geändert", changedEmoji, changedEntries, maxLength, builder.length() > 0)) {
            return builder.toString().trim();
        }
        if (!appendCategoryBlock(builder, "Entfernt", removedEmoji, removedEntries, maxLength, builder.length() > 0)) {
            return builder.toString().trim();
        }
        return builder.toString().trim();
    }

    private List<UpdateEntry> entriesOfType(List<UpdateEntry> entries, UpdateType type) {
        List<UpdateEntry> filtered = new ArrayList<>();
        for (UpdateEntry entry : entries) {
            if (entry.type() == type) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private boolean appendCategoryBlock(
            StringBuilder builder,
            String categoryTitle,
            String emoji,
            List<UpdateEntry> entries,
            int maxLength,
            boolean addLeadingSpacing
    ) {
        if (addLeadingSpacing && !appendWithLimit(builder, "\n\n", maxLength)) {
            return false;
        }
        if (!appendWithLimit(builder, emoji + " **" + categoryTitle + "**\n", maxLength)) {
            return false;
        }

        if (entries.isEmpty()) {
            return appendWithLimit(builder, "> Keine Einträge.\n", maxLength);
        }

        for (UpdateEntry entry : entries) {
            String line = emoji + " › " + entry.content() + " -> " + formatAuthor(entry.author()) + " `#" + entry.id() + "`\n";
            if (!appendWithLimit(builder, line, maxLength)) {
                return false;
            }
        }
        return true;
    }

    private String buildContainerCategoryBlock(String categoryTitle, String emoji, List<UpdateEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("### ").append(emoji).append(" ").append(categoryTitle).append("\n");
        if (entries.isEmpty()) {
            builder.append("> Keine Einträge.");
            return builder.toString();
        }

        boolean first = true;
        for (UpdateEntry entry : entries) {
            String block = buildContainerEntryBlock(entry);
            if (!first) {
                block = "\n" + block;
            }
            if (builder.length() + block.length() > CONTAINER_TEXT_LIMIT - 50) {
                builder.append("... (gekürzt)");
                break;
            }
            builder.append(block);
            first = false;
        }
        return builder.toString().trim();
    }

    private String buildContainerEntryBlock(UpdateEntry entry) {
        String contentBlock = toQuoteBlock(entry.content());
        return "• **`#" + entry.id() + "`** " + formatAuthor(entry.author()) + "\n" + contentBlock;
    }

    private String toQuoteBlock(String content) {
        String cleaned = content == null ? "" : content.replace("\r", "").trim();
        if (cleaned.isBlank()) {
            return "> (kein Text)";
        }

        String[] lines = cleaned.split("\n");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                builder.append("> ");
            } else {
                builder.append("> ").append(line);
            }
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private boolean appendWithLimit(StringBuilder builder, String text, int maxLength) {
        if (builder.length() + text.length() <= maxLength) {
            builder.append(text);
            return true;
        }
        if (builder.length() + "... (gekürzt)".length() <= maxLength) {
            builder.append("... (gekürzt)");
        }
        return false;
    }

    private String formatAuthor(String author) {
        if (author == null || author.isBlank()) {
            return "@Unbekannt";
        }

        String value = author.trim();
        Matcher mentionMatcher = USER_MENTION_PATTERN.matcher(value);
        if (mentionMatcher.matches()) {
            return "<@" + mentionMatcher.group(1) + ">";
        }

        if (USER_ID_PATTERN.matcher(value).matches()) {
            return "<@" + value + ">";
        }

        if (value.startsWith("@")) {
            return value;
        }

        return "@" + value;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String emojiFor(UpdateType type, BotConfig config) {
        return switch (type) {
            case ADDED -> resolveEmoji(config.addedEmoji(), config.addedEmojiId(), config.addedEmojiAnimated(), "added");
            case CHANGED -> resolveEmoji(config.changedEmoji(), config.changedEmojiId(), config.changedEmojiAnimated(), "changed");
            case REMOVED -> resolveEmoji(config.removedEmoji(), config.removedEmojiId(), config.removedEmojiAnimated(), "removed");
        };
    }

    private String resolveEmoji(String emoji, String emojiId, boolean animated, String fallbackName) {
        String value = emoji == null ? "" : emoji.trim();
        if (CUSTOM_EMOJI_PATTERN.matcher(value).matches()) {
            return value;
        }

        String id = emojiId == null ? "" : emojiId.trim();
        if (!id.isBlank()) {
            String name = fallbackName;
            if (value.startsWith(":") && value.endsWith(":") && value.length() > 2) {
                name = sanitizeEmojiName(value.substring(1, value.length() - 1));
            }
            return (animated ? "<a:" : "<:") + name + ":" + id + ">";
        }

        if (!value.isBlank()) {
            return value;
        }
        return ":" + sanitizeEmojiName(fallbackName) + ":";
    }

    private String sanitizeEmojiName(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9_]", "_");
        return cleaned.isBlank() ? "emoji" : cleaned;
    }
}
