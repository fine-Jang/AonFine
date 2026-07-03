package com.aonfine.auth.service.impl;

import com.aonfine.auth.service.UserVO;

public interface UserMapper {
    UserVO selectUser(String userId);

    void insertUser(UserVO userVO);
}
