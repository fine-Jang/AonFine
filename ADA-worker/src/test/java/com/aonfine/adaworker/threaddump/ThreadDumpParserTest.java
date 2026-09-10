package com.aonfine.adaworker.threaddump;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ThreadDumpParserTest {
    private final ThreadDumpParser parser = new ThreadDumpParser();

    @Test public void parsesThreadNameStateAndStack() {
        List<String> lines = Arrays.asList(
            "\"http-nio-8080-exec-1\" #23 daemon prio=5 os_prio=0 tid=0x00007f nid=0x1a runnable [0x00007fff]",
            "   java.lang.Thread.State: RUNNABLE",
            "\tat java.net.SocketInputStream.socketRead0(Native Method)",
            "\tat java.net.SocketInputStream.read(SocketInputStream.java:171)",
            ""
        );
        ThreadDumpParser.ParseResult result = parser.parseLines(lines);
        assertEquals(1, result.threads.size());
        ThreadDumpEntry t = result.threads.get(0);
        assertEquals("http-nio-8080-exec-1", t.name);
        assertEquals("RUNNABLE", t.state);
        assertEquals(2, t.stackFrames.size());
        assertTrue(t.stackFrames.get(1).contains("SocketInputStream.java:171"));
    }

    @Test public void detectsARealTwoThreadDeadlockFromLockLines() {
        List<String> lines = Arrays.asList(
            "\"Thread-A\" #1 prio=5 tid=0x1 nid=0x1 waiting for monitor entry [0x1]",
            "   java.lang.Thread.State: BLOCKED (on object monitor)",
            "\tat com.foo.A.method(A.java:10)",
            "\t- waiting to lock <0xBBBB> (a java.lang.Object)",
            "\t- locked <0xAAAA> (a java.lang.Object)",
            "",
            "\"Thread-B\" #2 prio=5 tid=0x2 nid=0x2 waiting for monitor entry [0x2]",
            "   java.lang.Thread.State: BLOCKED (on object monitor)",
            "\tat com.foo.B.method(B.java:20)",
            "\t- waiting to lock <0xAAAA> (a java.lang.Object)",
            "\t- locked <0xBBBB> (a java.lang.Object)",
            ""
        );
        ThreadDumpParser.ParseResult result = parser.parseLines(lines);
        assertEquals(2, result.threads.size());
        assertEquals(1, result.deadlockCycles.size());
        assertTrue(result.deadlockCycles.get(0).contains("Thread-A"));
        assertTrue(result.deadlockCycles.get(0).contains("Thread-B"));
    }

    @Test public void noCycleWhenThreadsAreIndependentlyBlocked() {
        List<String> lines = Arrays.asList(
            "\"Thread-A\" #1 prio=5 tid=0x1 nid=0x1 waiting for monitor entry [0x1]",
            "   java.lang.Thread.State: BLOCKED (on object monitor)",
            "\t- waiting to lock <0xCCCC> (a java.lang.Object)",
            "",
            "\"Thread-B\" #2 prio=5 tid=0x2 nid=0x2 runnable [0x2]",
            "   java.lang.Thread.State: RUNNABLE",
            "\t- locked <0xCCCC> (a java.lang.Object)",
            ""
        );
        ThreadDumpParser.ParseResult result = parser.parseLines(lines);
        assertTrue(result.deadlockCycles.isEmpty());
    }

    @Test public void capturesJstacksOwnDeadlockSectionVerbatimWhenPresent() {
        List<String> lines = Arrays.asList(
            "\"Thread-A\" #1 prio=5 tid=0x1 nid=0x1 runnable [0x1]",
            "   java.lang.Thread.State: RUNNABLE",
            "",
            "Found one Java-level deadlock:",
            "=============================",
            "\"Thread-A\":",
            "  waiting to lock monitor 0x1 (object 0xAAAA, a java.lang.Object),",
            "  which is held by \"Thread-B\""
        );
        ThreadDumpParser.ParseResult result = parser.parseLines(lines);
        assertNotNull(result.reportedDeadlockText);
        assertTrue(result.reportedDeadlockText.contains("Found one Java-level deadlock"));
    }
}
