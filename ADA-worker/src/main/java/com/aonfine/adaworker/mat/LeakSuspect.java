package com.aonfine.adaworker.mat;

import java.util.ArrayList;
import java.util.List;

/**
 * One "Problem Suspect N" entry from MAT's Leak Suspects report. Numeric fields are parsed
 * from MAT's own <strong> tags in the description paragraph -- never estimated. When a
 * sub-field can't be located in the expected position, it stays null/empty and the raw
 * description text is kept so nothing is silently lost.
 */
public final class LeakSuspect {
    public String title;                 // "Problem Suspect 1"
    public String rawDescriptionText;    // full paragraph text, verbatim, as a fallback ground truth
    public String holderIdentity;        // e.g. "java.lang.Thread @ 0xccdc3c80  default task-6" (may be a class, not always a thread)
    public String holderAddress;         // e.g. "0xccdc3c80"
    public String threadName;            // parsed out of holderIdentity when it is a java.lang.Thread
    public Long retainedBytes;
    public Double retainedPercent;
    public String accumulationPointClass;
    public Long accumulationPointBytes;
    public Double accumulationPointPercent;
    public final List<String> keywords = new ArrayList<>();
    public final List<String> significantStackFrames = new ArrayList<>(); // raw <li> text, includes file:line when present
    public String stackTraceHref;            // relative href to the plain stack page, e.g. "pages/24.html"
    public String stackTraceWithLocalsHref;  // relative href to the stack-with-locals page
    public String detailsHref;               // relative href to the "Details »" page

    /** Populated by a second pass that follows stackTraceHref and reads the <pre> block, one frame per line, verbatim. */
    public List<String> stackTraceLines = new ArrayList<>();
}
