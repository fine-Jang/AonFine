package com.aonfine.auth.service;

public interface UserService {
    void join(UserVO userVO);

    UserVO login(String userId, String password);

    UserVO selectUser(String userId);
}
