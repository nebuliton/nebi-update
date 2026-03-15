package dev.eministar.nebiupdate.time;

import dev.eministar.nebiupdate.config.BotConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class WeekService {

    public WeekWindow currentWeek(BotConfig config) {
        ZoneId zoneId = ZoneId.of(config.timezone());
        LocalDate today = LocalDate.now(zoneId);
        return weekForDate(today);
    }

    public WeekWindow previousWeek(BotConfig config) {
        return weekFromStart(currentWeek(config).start().minusWeeks(1));
    }

    public WeekWindow weekFromStart(LocalDate weekStart) {
        return new WeekWindow(weekStart, weekStart.plusDays(6));
    }

    public WeekWindow weekForDate(LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weekFromStart(weekStart);
    }

    public ZonedDateTime scheduledDateTime(WeekWindow week, BotConfig config) {
        ZoneId zoneId = ZoneId.of(config.timezone());
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(config.scheduleDay().toUpperCase(Locale.ROOT));
        LocalTime localTime = LocalTime.parse(config.scheduleTime());
        LocalDate date = week.start().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        if (date.isAfter(week.end())) {
            date = week.end();
        }
        return ZonedDateTime.of(date, localTime, zoneId);
    }

    public boolean isScheduleReached(WeekWindow week, BotConfig config) {
        ZoneId zoneId = ZoneId.of(config.timezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return !now.isBefore(scheduledDateTime(week, config));
    }
}
