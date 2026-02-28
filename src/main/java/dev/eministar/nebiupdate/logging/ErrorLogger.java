package dev.eministar.nebiupdate.logging;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public final class ErrorLogger {
    private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicLong COUNTER = new AtomicLong(0);

    private ErrorLogger() {
    }

    public static String capture(Logger logger, String tag, String summary, Throwable throwable) {
        String errorId = nextErrorId();
        String normalizedTag = normalizeTag(tag);
        String safeSummary = summary == null || summary.isBlank() ? "Unbekannter Fehler" : summary;

        // Short and clean signal in console/app log.
        logger.warn("[{}] {} | Fehler-ID={}", normalizedTag, safeSummary, errorId);
        // Full stacktrace is written to file appenders (console is filtered).
        logger.error("[{}] [Fehler-ID={}] {}", normalizedTag, errorId, safeSummary, throwable);
        return errorId;
    }

    public static String capture(Logger logger, String tag, Throwable throwable, String template, Object... args) {
        String summary = MessageFormatter.arrayFormat(template, args).getMessage();
        return capture(logger, tag, summary, throwable);
    }

    private static String nextErrorId() {
        long current = COUNTER.incrementAndGet();
        return ID_TIME_FORMAT.format(LocalDateTime.now()) + "-" + Long.toString(current, 36).toUpperCase();
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "GENERAL";
        }
        return tag.trim().toUpperCase();
    }
}
