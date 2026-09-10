package com.aonfine.adaworker.log;

import static org.junit.Assert.*;

import java.nio.file.Paths;

import org.junit.Test;

/**
 * Fixture is the REAL server.log from AonFine's intentional OOM test capture (see
 * MatReportParserTest). It has a genuine, previously-observed quirk: the hprof file's own
 * internal capture timestamp (2026-09-09 00:29:59) does not match when the OOM actually appears
 * in this log (2026-09-08 11:31:03) -- this test locks in the "don't silently trust one
 * timestamp" behavior against that real discrepancy, not a hypothetical one.
 */
public class ServerLogCorrelatorTest {
    private final ServerLogCorrelator correlator = new ServerLogCorrelator();

    @Test public void keywordSearchFindsTheRealOomRegardlessOfWindow() throws Exception {
        ServerLogCorrelator.CorrelationResult result = correlator.correlate(
                Paths.get("src/test/resources/log-fixture/server.log"), null, 10);
        assertFalse(result.keywordExcerpt.isEmpty());
        assertTrue(result.keywordExcerpt.stream().anyMatch(l -> l.contains("OutOfMemoryError")));
    }

    @Test public void windowAroundTheWrongHprofTimestampFindsNothingAndDoesNotOverlap() throws Exception {
        // This is the hprof's own internal "Date"/"Time" header value from the real MAT report fixture.
        ServerLogCorrelator.CorrelationResult result = correlator.correlate(
                Paths.get("src/test/resources/log-fixture/server.log"), "2026-09-09 00:29:59", 30);
        assertTrue("no log lines should fall in a window around a timestamp the log never reaches",
                result.windowExcerpt.isEmpty());
        assertFalse(result.keywordExcerpt.isEmpty());
        assertFalse("window and keyword evidence must not be reported as agreeing when they don't overlap",
                result.windowAndKeywordOverlap);
    }

    @Test public void windowAroundTheActualLogTimeOfTheOomOverlapsWithKeywordMatches() throws Exception {
        ServerLogCorrelator.CorrelationResult result = correlator.correlate(
                Paths.get("src/test/resources/log-fixture/server.log"), "2026-09-08 11:31:03", 5);
        assertFalse(result.windowExcerpt.isEmpty());
        assertTrue(result.windowAndKeywordOverlap);
    }

    @Test public void recordsFirstAndLastTimestampSeenInTheLog() throws Exception {
        ServerLogCorrelator.CorrelationResult result = correlator.correlate(
                Paths.get("src/test/resources/log-fixture/server.log"), null, 10);
        assertEquals("2026-09-08 11:28:27", result.logFirstTimestamp);
        assertTrue(result.logLastTimestamp.startsWith("2026-09-08"));
    }
}
