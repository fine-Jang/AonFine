package com.aonfine.adaworker.evidence;

import static org.junit.Assert.*;

import org.junit.Test;

public class EvidenceMaskerTest {
    @Test public void masksPasswordAssignments() {
        String masked = EvidenceMasker.mask("connecting with password=hunter2 to db");
        assertFalse(masked.contains("hunter2"));
        assertTrue(masked.contains("[MASKED]"));
    }

    @Test public void masksBearerTokens() {
        String masked = EvidenceMasker.mask("Authorization: Bearer abc123.def456");
        assertFalse(masked.contains("abc123.def456"));
    }

    @Test public void leavesOrdinaryLogLinesUntouched() {
        String line = "2026-09-08 11:31:03,055 ERROR [io.undertow.request] (default task-6) UT005023: Exception handling request";
        assertEquals(line, EvidenceMasker.mask(line));
    }
}
