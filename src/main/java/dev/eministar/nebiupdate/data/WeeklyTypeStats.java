package dev.eministar.nebiupdate.data;

import java.time.LocalDate;

public record WeeklyTypeStats(
        LocalDate weekStart,
        int added,
        int changed,
        int removed
) {
    public int total() {
        return added + changed + removed;
    }
}
