package com.aonfine.adaworker.mat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MAT's "Heap Dump Overview" key/value table, taken verbatim from the batch report
 * (System_Overview index.html). Every field here is copied text/numbers from MAT's own
 * output -- nothing is inferred or generated.
 */
public final class HeapOverview {
    /** Raw key -> value pairs exactly as MAT rendered them (e.g. "Used heap dump" -> "1.8 GB"). */
    public final Map<String, String> fields = new LinkedHashMap<>();

    public String get(String key) {
        return fields.get(key);
    }

    /** "File length" is the one field MAT always renders as a plain decimal byte count. */
    public Long fileLengthBytes() {
        String v = fields.get("File length");
        if (v == null) return null;
        try {
            return Long.parseLong(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
