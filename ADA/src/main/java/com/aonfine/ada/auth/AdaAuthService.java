package com.aonfine.ada.auth;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

@Service("adaAuthService")
public class AdaAuthService {

    @Resource(name = "adaUserMapper")
    private AdaUserMapper adaUserMapper;

    /**
     * AonFine과 동일한 SHA-256(password) == TB_USER.PASSWORD_HASH 비교로 로그인 처리한다.
     * ADA 전용 세션을 사용하며 AonFine의 로그인 세션과는 별개다.
     */
    public AdaUserVO login(String userId, String rawPassword) {
        if (userId == null || userId.isEmpty() || rawPassword == null) {
            return null;
        }
        AdaUserVO user = adaUserMapper.selectUser(userId);
        if (user == null || user.getPasswordHash() == null) {
            return null;
        }
        String hash = AdaPasswordUtil.sha256(rawPassword);
        if (!hash.equalsIgnoreCase(user.getPasswordHash())) {
            return null;
        }
        return user;
    }
}
