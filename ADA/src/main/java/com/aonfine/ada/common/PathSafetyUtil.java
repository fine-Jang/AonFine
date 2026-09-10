package com.aonfine.ada.common;

/**
 * tar 엔트리 경로 안전성 검사. 실제로 디스크에 풀지는 않지만(순차 스트리밍 검증만 수행),
 * 향후 확장이나 다른 경로에서 재사용될 수 있어 엄격하게 검사한다.
 */
public final class PathSafetyUtil {

    private PathSafetyUtil() {
    }

    /**
     * 위험한 tar 엔트리 이름인지 검사한다: 절대경로, 상위 디렉터리 이동(..), 널바이트를 거절한다.
     */
    public static boolean isDangerousEntryName(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.indexOf('\0') >= 0) {
            return true;
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        // Windows 드라이브 문자 경로 (예: C:) 도 거절
        return normalized.length() >= 2 && normalized.charAt(1) == ':';
    }
}
