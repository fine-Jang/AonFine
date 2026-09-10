package com.aonfine.ada.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PathSafetyUtilTest {

    @Test
    public void rejectsParentDirectoryTraversal() {
        assertTrue(PathSafetyUtil.isDangerousEntryName("../etc/passwd"));
        assertTrue(PathSafetyUtil.isDangerousEntryName("a/../../b"));
        assertTrue(PathSafetyUtil.isDangerousEntryName(".."));
    }

    @Test
    public void rejectsAbsolutePaths() {
        assertTrue(PathSafetyUtil.isDangerousEntryName("/etc/passwd"));
        assertTrue(PathSafetyUtil.isDangerousEntryName("C:/Windows/System32"));
    }

    @Test
    public void rejectsEmptyOrNull() {
        assertTrue(PathSafetyUtil.isDangerousEntryName(null));
        assertTrue(PathSafetyUtil.isDangerousEntryName(""));
    }

    @Test
    public void allowsOrdinaryRelativePaths() {
        assertFalse(PathSafetyUtil.isDangerousEntryName("logs/app.log"));
        assertFalse(PathSafetyUtil.isDangerousEntryName("heap 2024-01-01.hprof"));
        assertFalse(PathSafetyUtil.isDangerousEntryName("readme.txt"));
    }
}
