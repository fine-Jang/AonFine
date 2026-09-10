package com.aonfine.adaworker.threaddump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for standalone HotSpot-format thread dumps (jstack / kill -3 output).
 * Every field is read directly from the dump text -- states, stacks and lock addresses are
 * never inferred. Deadlock detection is a real cycle search over the "waiting to lock" / "locked"
 * graph built from THIS dump's own lines, not a guess.
 */
public final class ThreadDumpParser {

    private static final Pattern HEADER = Pattern.compile("^\"(.*)\"\\s.*$");
    private static final Pattern STATE = Pattern.compile("java\\.lang\\.Thread\\.State:\\s*(\\S+)");
    private static final Pattern LOCKED = Pattern.compile("-\\s*locked\\s*<(0x[0-9a-fA-F]+)>");
    private static final Pattern WAITING_TO_LOCK = Pattern.compile("-\\s*waiting to lock\\s*<(0x[0-9a-fA-F]+)>");
    private static final Pattern PARKING_TO_WAIT = Pattern.compile("-\\s*parking to wait for\\s*<(0x[0-9a-fA-F]+)>");

    public static final class ParseResult {
        public final List<ThreadDumpEntry> threads = new ArrayList<>();
        /** Cycles found by following waiting-thread -> holder-thread edges. Each cycle is an ordered list of thread names. */
        public final List<List<String>> deadlockCycles = new ArrayList<>();
        /** Verbatim "Found N deadlock" section text from the dump itself, if the tool that produced it already reported one. */
        public String reportedDeadlockText;
    }

    public ParseResult parse(Path threadDumpFile) throws IOException {
        List<String> lines = Files.readAllLines(threadDumpFile);
        return parseLines(lines);
    }

    public ParseResult parseLines(List<String> lines) {
        ParseResult result = new ParseResult();
        ThreadDumpEntry current = null;
        StringBuilder reportedDeadlock = null;
        for (String line : lines) {
            if (line.contains("Found") && line.toLowerCase().contains("deadlock")) {
                reportedDeadlock = new StringBuilder();
            }
            if (reportedDeadlock != null) {
                reportedDeadlock.append(line).append('\n');
            }
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                current = new ThreadDumpEntry();
                current.name = header.group(1);
                current.rawHeaderLine = line;
                result.threads.add(current);
                continue;
            }
            if (current == null) continue;
            Matcher state = STATE.matcher(line);
            if (state.find()) {
                current.state = state.group(1);
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("at ")) {
                current.stackFrames.add(trimmed);
                continue;
            }
            Matcher locked = LOCKED.matcher(line);
            if (locked.find()) {
                current.lockedMonitors.add(locked.group(1));
            }
            Matcher waiting = WAITING_TO_LOCK.matcher(line);
            if (waiting.find()) {
                current.waitingToLock.add(waiting.group(1));
            }
            Matcher parking = PARKING_TO_WAIT.matcher(line);
            if (parking.find()) {
                current.waitingToLock.add(parking.group(1));
            }
        }
        if (reportedDeadlock != null) {
            result.reportedDeadlockText = reportedDeadlock.toString().trim();
        }
        result.deadlockCycles.addAll(findLockCycles(result.threads));
        return result;
    }

    /** Builds a wait-for graph (thread -> thread holding the monitor it wants) and returns any real cycles found in it. */
    private List<List<String>> findLockCycles(List<ThreadDumpEntry> threads) {
        Map<String, String> monitorOwner = new HashMap<>(); // monitor address -> thread name that holds it
        for (ThreadDumpEntry t : threads) {
            for (String monitor : t.lockedMonitors) monitorOwner.put(monitor, t.name);
        }
        Map<String, String> waitsFor = new HashMap<>(); // thread name -> thread name it is blocked on
        for (ThreadDumpEntry t : threads) {
            for (String monitor : t.waitingToLock) {
                String owner = monitorOwner.get(monitor);
                if (owner != null && !owner.equals(t.name)) {
                    waitsFor.put(t.name, owner);
                    break;
                }
            }
        }
        List<List<String>> cycles = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (String start : waitsFor.keySet()) {
            List<String> path = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String cursor = start;
            while (cursor != null && !seen.contains(cursor)) {
                seen.add(cursor);
                path.add(cursor);
                cursor = waitsFor.get(cursor);
            }
            if (cursor != null && path.contains(cursor)) {
                List<String> cycle = path.subList(path.indexOf(cursor), path.size());
                String key = new HashSet<>(cycle).toString();
                if (reported.add(key)) cycles.add(new ArrayList<>(cycle));
            }
        }
        return cycles;
    }
}
