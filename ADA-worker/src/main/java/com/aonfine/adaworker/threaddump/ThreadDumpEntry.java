package com.aonfine.adaworker.threaddump;

import java.util.ArrayList;
import java.util.List;

/** One thread's block from a standalone HotSpot thread dump (jstack / kill -3), parsed verbatim. */
public final class ThreadDumpEntry {
    public String name;
    public String state;                 // e.g. RUNNABLE, BLOCKED, WAITING, TIMED_WAITING
    public String rawHeaderLine;
    public final List<String> stackFrames = new ArrayList<>();   // "at com.foo.Bar.method(Bar.java:123)"
    public final List<String> lockedMonitors = new ArrayList<>(); // monitor addresses this thread holds, e.g. "0x00000123"
    public final List<String> waitingToLock = new ArrayList<>();  // monitor addresses this thread is blocked waiting for
}
