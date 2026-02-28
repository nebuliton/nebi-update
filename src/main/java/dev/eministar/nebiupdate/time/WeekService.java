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
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeekWindow(weekStart, weekStart.plusDays(6));
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
