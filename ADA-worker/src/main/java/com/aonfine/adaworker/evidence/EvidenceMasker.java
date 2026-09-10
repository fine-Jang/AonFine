package com.aonfine.adaworker.evidence;

import java.util.regex.Pattern;

/** Best-effort redaction of secret-shaped substrings before any log/text excerpt reaches the Claude prompt. */
public final class EvidenceMasker {
    private static final Pattern[] PATTERNS = {
        Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+"),
        Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._-]+"),
        Pattern.compile("(?i)Authorization:\\s*\\S+"),
        Pattern.compile("(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*\\S+"),
        Pattern.compile("jdbc:[a-zA-Z]+:[^\\s]*://?[^\\s]*:[^\\s@]+@?[^\\s]*"),
    };

    private EvidenceMasker() { }

    public static String mask(String line) {
        String result = line;
        for (Pattern p : PATTERNS) {
            result = p.matcher(result).replaceAll(matchResult -> maskedLabel(matchResult.group()));
        }
        return result;
    }

    private static String maskedLabel(String original) {
        int colonOrEquals = Math.max(original.indexOf(':'), original.indexOf('='));
        String prefix = colonOrEquals > 0 ? original.substring(0, colonOrEquals + 1) : "";
        return prefix + " [MASKED]";
    }
}
