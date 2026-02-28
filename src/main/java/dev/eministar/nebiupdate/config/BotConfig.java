package dev.eministar.nebiupdate.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BotConfig {
    private static final DateTimeFormatter OUTPUT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static final String KEY_GUILD_ID = "guild_id";
    public static final String KEY_CHANNEL_ID = "channel_id";
    public static final String KEY_TIMEZONE = "timezone";
    public static final String KEY_SCHEDULE_DAY = "schedule_day";
    public static final String KEY_SCHEDULE_TIME = "schedule_time";
    public static final String KEY_TITLE_EMOJI = "title_emoji";
    public static final String KEY_TITLE_EMOJI_ID = "title_emoji_id";
    public static final String KEY_TITLE_EMOJI_ANIMATED = "title_emoji_animated";
    public static final String KEY_TITLE_TEXT = "title_text";
    public static final String KEY_ADDED_EMOJI = "added_emoji";
    public static final String KEY_ADDED_EMOJI_ID = "added_emoji_id";
    public static final String KEY_ADDED_EMOJI_ANIMATED = "added_emoji_animated";
    public static final String KEY_CHANGED_EMOJI = "changed_emoji";
    public static final String KEY_CHANGED_EMOJI_ID = "changed_emoji_id";
    public static final String KEY_CHANGED_EMOJI_ANIMATED = "changed_emoji_animated";
    public static final String KEY_REMOVED_EMOJI = "removed_emoji";
    public static final String KEY_REMOVED_EMOJI_ID = "removed_emoji_id";
    public static final String KEY_REMOVED_EMOJI_ANIMATED = "removed_emoji_animated";
    public static final String KEY_NOTICE_EMOJI = "notice_emoji";
    public static final String KEY_NOTICE_EMOJI_ID = "notice_emoji_id";
    public static final String KEY_NOTICE_EMOJI_ANIMATED = "notice_emoji_animated";
    public static final String KEY_NOTICE_TEXT = "notice_text";
    public static final String KEY_NO_CHANGE_TEXT = "no_change_text";
    public static final String KEY_SPACER = "spacer";
    public static final String KEY_DASHBOARD_HOST = "dashboard_host";
    public static final String KEY_DASHBOARD_PORT = "dashboard_port";

    private static final Map<String, String> DEFAULTS = createDefaults();
    private static final Set<String> SUPPORTED_KEYS = DEFAULTS.keySet();

    private final String guildId;
    private final String channelId;
    private final String timezone;
    private final String scheduleDay;
    private final String scheduleTime;
    private final String titleEmoji;
    private final String titleEmojiId;
    private final boolean titleEmojiAnimated;
    private final String titleText;
    private final String addedEmoji;
    private final String addedEmojiId;
    private final boolean addedEmojiAnimated;
    private final String changedEmoji;
    private final String changedEmojiId;
    private final boolean changedEmojiAnimated;
    private final String removedEmoji;
    private final String removedEmojiId;
    private final boolean removedEmojiAnimated;
    private final String noticeEmoji;
    private final String noticeEmojiId;
    private final boolean noticeEmojiAnimated;
    private final String noticeText;
    private final String noChangeText;
    private final String spacer;
    private final String dashboardHost;
    private final int dashboardPort;

    private BotConfig(
            String guildId,
            String channelId,
            String timezone,
            String scheduleDay,
            String scheduleTime,
            String titleEmoji,
            String titleEmojiId,
            boolean titleEmojiAnimated,
            String titleText,
            String addedEmoji,
            String addedEmojiId,
            boolean addedEmojiAnimated,
            String changedEmoji,
            String changedEmojiId,
            boolean changedEmojiAnimated,
            String removedEmoji,
            String removedEmojiId,
            boolean removedEmojiAnimated,
            String noticeEmoji,
            String noticeEmojiId,
            boolean noticeEmojiAnimated,
            String noticeText,
            String noChangeText,
            String spacer,
            String dashboardHost,
            int dashboardPort
    ) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.timezone = timezone;
        this.scheduleDay = scheduleDay;
        this.scheduleTime = scheduleTime;
        this.titleEmoji = titleEmoji;
        this.titleEmojiId = titleEmojiId;
        this.titleEmojiAnimated = titleEmojiAnimated;
        this.titleText = titleText;
        this.addedEmoji = addedEmoji;
        this.addedEmojiId = addedEmojiId;
        this.addedEmojiAnimated = addedEmojiAnimated;
        this.changedEmoji = changedEmoji;
        this.changedEmojiId = changedEmojiId;
        this.changedEmojiAnimated = changedEmojiAnimated;
        this.removedEmoji = removedEmoji;
        this.removedEmojiId = removedEmojiId;
        this.removedEmojiAnimated = removedEmojiAnimated;
        this.noticeEmoji = noticeEmoji;
        this.noticeEmojiId = noticeEmojiId;
        this.noticeEmojiAnimated = noticeEmojiAnimated;
        this.noticeText = noticeText;
        this.noChangeText = noChangeText;
        this.spacer = spacer;
        this.dashboardHost = dashboardHost;
        this.dashboardPort = dashboardPort;
    }

    private static Map<String, String> createDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(KEY_GUILD_ID, "");
        defaults.put(KEY_CHANNEL_ID, "");
        defaults.put(KEY_TIMEZONE, "Europe/Berlin");
        defaults.put(KEY_SCHEDULE_DAY, "MONDAY");
        defaults.put(KEY_SCHEDULE_TIME, "12:00");
        defaults.put(KEY_TITLE_EMOJI, ":gameboy:");
        defaults.put(KEY_TITLE_EMOJI_ID, "");
        defaults.put(KEY_TITLE_EMOJI_ANIMATED, "false");
        defaults.put(KEY_TITLE_TEXT, "WOCHEN-RÜCKBLICK");
        defaults.put(KEY_ADDED_EMOJI, ":PointGreen:");
        defaults.put(KEY_ADDED_EMOJI_ID, "");
        defaults.put(KEY_ADDED_EMOJI_ANIMATED, "false");
        defaults.put(KEY_CHANGED_EMOJI, ":PointOrange:");
        defaults.put(KEY_CHANGED_EMOJI_ID, "");
        defaults.put(KEY_CHANGED_EMOJI_ANIMATED, "false");
        defaults.put(KEY_REMOVED_EMOJI, ":PointRed:");
        defaults.put(KEY_REMOVED_EMOJI_ID, "");
        defaults.put(KEY_REMOVED_EMOJI_ANIMATED, "false");
        defaults.put(KEY_NOTICE_EMOJI, ":arrow2:");
        defaults.put(KEY_NOTICE_EMOJI_ID, "");
        defaults.put(KEY_NOTICE_EMOJI_ANIMATED, "false");
        defaults.put(KEY_NOTICE_TEXT, "Diese Nachricht wird mit jeder neuen Änderung dieser Woche bearbeitet.");
        defaults.put(KEY_NO_CHANGE_TEXT, "In dieser Woche gab es keine Änderung.");
        defaults.put(KEY_SPACER, " ");
        defaults.put(KEY_DASHBOARD_HOST, "0.0.0.0");
        defaults.put(KEY_DASHBOARD_PORT, "8080");
        return Map.copyOf(defaults);
    }

    public static Set<String> supportedKeys() {
        return SUPPORTED_KEYS;
    }

    public static Map<String, String> defaultMap() {
        return new LinkedHashMap<>(DEFAULTS);
    }

    public static BotConfig from(Map<String, String> raw) {
        Map<String, String> source = new LinkedHashMap<>(DEFAULTS);
        source.putAll(raw);

        String timezone = normalize(source.get(KEY_TIMEZONE));
        String scheduleDay = normalize(source.get(KEY_SCHEDULE_DAY)).toUpperCase(Locale.ROOT);
        String scheduleTime = normalizeScheduleTime(source.get(KEY_SCHEDULE_TIME));

        try {
            ZoneId.of(timezone);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
        try {
            DayOfWeek.valueOf(scheduleDay);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid schedule day: " + scheduleDay);
        }
        int dashboardPort = parsePort(normalize(source.get(KEY_DASHBOARD_PORT)));
        String dashboardHost = normalize(source.get(KEY_DASHBOARD_HOST));
        if (dashboardHost.isBlank()) {
            dashboardHost = "0.0.0.0";
        }

        return new BotConfig(
                normalize(source.get(KEY_GUILD_ID)),
                normalize(source.get(KEY_CHANNEL_ID)),
                timezone,
                scheduleDay,
                scheduleTime,
                normalize(source.get(KEY_TITLE_EMOJI)),
                normalize(source.get(KEY_TITLE_EMOJI_ID)),
                parseBoolean(source.get(KEY_TITLE_EMOJI_ANIMATED)),
                normalize(source.get(KEY_TITLE_TEXT)),
                normalize(source.get(KEY_ADDED_EMOJI)),
                normalize(source.get(KEY_ADDED_EMOJI_ID)),
                parseBoolean(source.get(KEY_ADDED_EMOJI_ANIMATED)),
                normalize(source.get(KEY_CHANGED_EMOJI)),
                normalize(source.get(KEY_CHANGED_EMOJI_ID)),
                parseBoolean(source.get(KEY_CHANGED_EMOJI_ANIMATED)),
                normalize(source.get(KEY_REMOVED_EMOJI)),
                normalize(source.get(KEY_REMOVED_EMOJI_ID)),
                parseBoolean(source.get(KEY_REMOVED_EMOJI_ANIMATED)),
                normalize(source.get(KEY_NOTICE_EMOJI)),
                normalize(source.get(KEY_NOTICE_EMOJI_ID)),
                parseBoolean(source.get(KEY_NOTICE_EMOJI_ANIMATED)),
                normalize(source.get(KEY_NOTICE_TEXT)),
                normalize(source.get(KEY_NO_CHANGE_TEXT)),
                normalize(source.get(KEY_SPACER)),
                dashboardHost,
                dashboardPort
        );
    }

    private static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) {
                throw new IllegalArgumentException("dashboard_port must be between 1 and 65535");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid dashboard_port: " + value);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean parseBoolean(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("ja")
                || normalized.equals("on");
    }

    private static String normalizeScheduleTime(String value) {
        String raw = normalize(value);
        if (raw.isBlank()) {
            throw new IllegalArgumentException("Invalid schedule time (HH:mm): " + raw);
        }

        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.equals("24:00") || upper.equals("24:00:00")) {
            return "00:00";
        }

        DateTimeFormatter[] formatters = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("H:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH)
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalTime parsed = LocalTime.parse(upper, formatter);
                return parsed.format(OUTPUT_TIME_FORMAT);
            } catch (Exception ignored) {
                // Try next supported formatter.
            }
        }

        throw new IllegalArgumentException("Invalid schedule time (HH:mm): " + raw);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(KEY_GUILD_ID, guildId);
        map.put(KEY_CHANNEL_ID, channelId);
        map.put(KEY_TIMEZONE, timezone);
        map.put(KEY_SCHEDULE_DAY, scheduleDay);
        map.put(KEY_SCHEDULE_TIME, scheduleTime);
        map.put(KEY_TITLE_EMOJI, titleEmoji);
        map.put(KEY_TITLE_EMOJI_ID, titleEmojiId);
        map.put(KEY_TITLE_EMOJI_ANIMATED, Boolean.toString(titleEmojiAnimated));
        map.put(KEY_TITLE_TEXT, titleText);
        map.put(KEY_ADDED_EMOJI, addedEmoji);
        map.put(KEY_ADDED_EMOJI_ID, addedEmojiId);
        map.put(KEY_ADDED_EMOJI_ANIMATED, Boolean.toString(addedEmojiAnimated));
        map.put(KEY_CHANGED_EMOJI, changedEmoji);
        map.put(KEY_CHANGED_EMOJI_ID, changedEmojiId);
        map.put(KEY_CHANGED_EMOJI_ANIMATED, Boolean.toString(changedEmojiAnimated));
        map.put(KEY_REMOVED_EMOJI, removedEmoji);
        map.put(KEY_REMOVED_EMOJI_ID, removedEmojiId);
        map.put(KEY_REMOVED_EMOJI_ANIMATED, Boolean.toString(removedEmojiAnimated));
        map.put(KEY_NOTICE_EMOJI, noticeEmoji);
        map.put(KEY_NOTICE_EMOJI_ID, noticeEmojiId);
        map.put(KEY_NOTICE_EMOJI_ANIMATED, Boolean.toString(noticeEmojiAnimated));
        map.put(KEY_NOTICE_TEXT, noticeText);
        map.put(KEY_NO_CHANGE_TEXT, noChangeText);
        map.put(KEY_SPACER, spacer);
        map.put(KEY_DASHBOARD_HOST, dashboardHost);
        map.put(KEY_DASHBOARD_PORT, Integer.toString(dashboardPort));
        return map;
    }

    public String guildId() {
        return guildId;
    }

    public String channelId() {
        return channelId;
    }

    public String timezone() {
        return timezone;
    }

    public String scheduleDay() {
        return scheduleDay;
    }

    public String scheduleTime() {
        return scheduleTime;
    }

    public String titleEmoji() {
        return titleEmoji;
    }

    public String titleEmojiId() {
        return titleEmojiId;
    }

    public boolean titleEmojiAnimated() {
        return titleEmojiAnimated;
    }

    public String titleText() {
        return titleText;
    }

    public String addedEmoji() {
        return addedEmoji;
    }

    public String addedEmojiId() {
        return addedEmojiId;
    }

    public boolean addedEmojiAnimated() {
        return addedEmojiAnimated;
    }

    public String changedEmoji() {
        return changedEmoji;
    }

    public String changedEmojiId() {
        return changedEmojiId;
    }

    public boolean changedEmojiAnimated() {
        return changedEmojiAnimated;
    }

    public String removedEmoji() {
        return removedEmoji;
    }

    public String removedEmojiId() {
        return removedEmojiId;
    }

    public boolean removedEmojiAnimated() {
        return removedEmojiAnimated;
    }

    public String noticeEmoji() {
        return noticeEmoji;
    }

    public String noticeEmojiId() {
        return noticeEmojiId;
    }

    public boolean noticeEmojiAnimated() {
        return noticeEmojiAnimated;
    }

    public String noticeText() {
        return noticeText;
    }

    public String noChangeText() {
        return noChangeText;
    }

    public String spacer() {
        return spacer;
    }

    public String dashboardHost() {
        return dashboardHost;
    }

    public int dashboardPort() {
        return dashboardPort;
    }
}
