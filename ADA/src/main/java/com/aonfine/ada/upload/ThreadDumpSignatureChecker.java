package com.aonfine.ada.upload;

import java.nio.charset.StandardCharsets;

/**
 * 텍스트 내용이 실제 스레드 덤프처럼 보이는지 스트리밍으로 확인한다.
 *
 * 지원 범위: HotSpot/OpenJDK 계열 JVM이 jstack 또는 kill -3(SIGQUIT)으로 생성하는 표준
 * 스레드 덤프 형식만 인식한다 - "java.lang.Thread.State:" 마커(모든 HotSpot 스레드 덤프에
 * 스레드마다 한 줄씩 등장) 존재 여부로 판단한다. 다른 JVM 벤더나 APM 도구가 만드는 별도
 * 포맷의 스레드 덤프는 이 검사로 인식되지 않을 수 있다 - 확장자만 맞는 임의의 텍스트 파일이
 * 통과하지 못하게 막는 최소한의 안전장치이며, 완전한 형식 파서는 아니다.
 */
class ThreadDumpSignatureChecker {

    private static final byte[] MARKER = "java.lang.Thread.State:".getBytes(StandardCharsets.US_ASCII);

    private byte[] carry = new byte[0];
    private boolean found = false;

    void accept(byte[] buf, int off, int len) {
        if (found) {
            return;
        }
        byte[] combined = new byte[carry.length + len];
        System.arraycopy(carry, 0, combined, 0, carry.length);
        System.arraycopy(buf, off, combined, carry.length, len);

        if (indexOf(combined, MARKER) >= 0) {
            found = true;
            carry = new byte[0];
            return;
        }

        int keep = Math.min(combined.length, MARKER.length - 1);
        carry = new byte[keep];
        System.arraycopy(combined, combined.length - keep, carry, 0, keep);
    }

    boolean isFound() {
        return found;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
