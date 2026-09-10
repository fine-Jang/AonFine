package com.aonfine.ada.auth;

import java.io.Serializable;

/**
 * TB_USER 조회 전용 VO. ADA는 TB_USER를 SELECT만 하며 계정 생성/수정 기능을 갖지 않는다.
 */
public class AdaUserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String passwordHash;
    private String userName;
    private String roleCode;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }
}
