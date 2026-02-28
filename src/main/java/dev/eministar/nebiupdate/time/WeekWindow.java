package dev.eministar.nebiupdate.time;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record WeekWindow(LocalDate start, LocalDate end) {
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    public String label() {
        return start.format(LABEL_FORMAT) + " - " + end.format(LABEL_FORMAT);
    }
}
