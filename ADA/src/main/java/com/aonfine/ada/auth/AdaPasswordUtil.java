package com.aonfine.ada.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AonFine의 com.aonfine.common.util.PasswordUtil과 동일한 SHA-256 해시 로직을 그대로 복제한다.
 * ADA는 별도 WAR로 배포되어 AonFine 클래스를 직접 재사용할 수 없으므로, TB_USER.PASSWORD_HASH와
 * 호환되는 결과를 내기 위해 알고리즘을 동일하게 유지한다. 이 코드를 수정하려면 AonFine 쪽
 * PasswordUtil도 함께 검토해야 한다.
 */
final class AdaPasswordUtil {
    private AdaPasswordUtil() {
    }

    static String sha256(String value) {
        if (value == null) {
            value = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
