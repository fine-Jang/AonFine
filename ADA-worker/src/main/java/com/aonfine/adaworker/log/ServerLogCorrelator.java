package com.aonfine.adaworker.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Correlates a server.log with a dump's capture time. Real-world capture: a heap dump's own
 * internal timestamp and the moment an OOM/error is actually logged can disagree (clock
 * changes, copy timestamps, manually-assembled evidence bundles) -- this class never assumes
 * they match. It reports both a time-window excerpt (if a candidate timestamp is given) and an
 * independent keyword-matched excerpt, and flags when they do not overlap so the report can say
 * so honestly instead of picking one silently.
 */
public final class ServerLogCorrelator {

    private static final Pattern TIMESTAMPED_LINE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> DEFAULT_KEYWORDS = List.of(
            "OutOfMemoryError", "OOM", "Exception", "ERROR", "deadlock", "Deadlock");

    public static final class CorrelationResult {
        public String candidateTimestamp;                 // the timestamp this window search was based on, verbatim, or null
        public List<String> windowExcerpt = new ArrayList<>();   // lines within the window of candidateTimestamp
        public List<String> keywordExcerpt = new ArrayList<>();  // lines matching error/OOM/deadlock keywords, independent of window
        public boolean windowAndKeywordOverlap;
        public String logFirstTimestamp;
        public String logLastTimestamp;
    }

    /**
     * @param candidateTimestamp e.g. from the hprof's own "Date"/"Time" header fields, formatted "yyyy-MM-dd HH:mm:ss", or null to skip windowing
     * @param windowMinutes minutes before/after candidateTimestamp to include
     */
    public CorrelationResult correlate(Path serverLog, String candidateTimestamp, int windowMinutes) throws IOException {
        List<String> lines = Files.readAllLines(serverLog);
        CorrelationResult result = new CorrelationResult();
        result.candidateTimestamp = candidateTimestamp;

        LocalDateTime[] range = null;
        if (candidateTimestamp != null) {
            try {
                LocalDateTime t = LocalDateTime.parse(candidateTimestamp, TS_FORMAT);
                range = new LocalDateTime[] { t.minusMinutes(windowMinutes), t.plusMinutes(windowMinutes) };
            } catch (Exception ignored) {
                // Unparseable candidate timestamp: skip windowing rather than guess a format.
            }
        }

        for (String line : lines) {
            Matcher m = TIMESTAMPED_LINE.matcher(line);
            if (m.find()) {
                String ts = m.group(1);
                if (result.logFirstTimestamp == null) result.logFirstTimestamp = ts;
                result.logLastTimestamp = ts;
                if (range != null) {
                    try {
                        LocalDateTime lineTime = LocalDateTime.parse(ts, TS_FORMAT);
                        if (!lineTime.isBefore(range[0]) && !lineTime.isAfter(range[1])) {
                            result.windowExcerpt.add(line);
                        }
                    } catch (Exception ignored) { /* line's own timestamp didn't parse; skip it for windowing only */ }
                }
            }
            for (String keyword : DEFAULT_KEYWORDS) {
                if (line.contains(keyword)) {
                    result.keywordExcerpt.add(line);
                    break;
                }
            }
        }

        if (!result.windowExcerpt.isEmpty() && !result.keywordExcerpt.isEmpty()) {
            result.windowAndKeywordOverlap = !java.util.Collections.disjoint(result.windowExcerpt, result.keywordExcerpt);
        }
        return result;
    }
}
