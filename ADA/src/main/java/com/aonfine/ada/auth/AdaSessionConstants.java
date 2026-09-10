package com.aonfine.ada.auth;

public final class AdaSessionConstants {
    private AdaSessionConstants() {
    }

    /** AonFine의 "loginUser"와 다른 별도 세션 키. 두 앱은 서로 다른 로그인 세션을 사용한다. */
    public static final String LOGIN_SESSION_KEY = "adaLoginUser";
}
