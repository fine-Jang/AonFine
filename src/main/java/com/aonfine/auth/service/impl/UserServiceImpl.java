package com.aonfine.auth.service.impl;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aonfine.auth.service.UserService;
import com.aonfine.auth.service.UserVO;
import com.aonfine.common.util.PasswordUtil;

@Service("userService")
public class UserServiceImpl implements UserService {

    @Resource(name = "userMapper")
    private UserMapper userMapper;

    public void join(UserVO userVO) {
        validateJoin(userVO);
        if (userMapper.selectUser(userVO.getUserId()) != null) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        userVO.setPasswordHash(PasswordUtil.sha256(userVO.getPassword()));
        userMapper.insertUser(userVO);
    }

    public UserVO login(String userId, String password) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(password)) {
            return null;
        }
        UserVO user = userMapper.selectUser(userId);
        if (user == null) {
            return null;
        }
        String inputHash = PasswordUtil.sha256(password);
        if (!inputHash.equals(user.getPasswordHash())) {
            return null;
        }
        user.setPassword(null);
        user.setPasswordHash(null);
        return user;
    }

    public UserVO selectUser(String userId) {
        return userMapper.selectUser(userId);
    }

    private void validateJoin(UserVO userVO) {
        if (userVO == null || !StringUtils.hasText(userVO.getUserId())) {
            throw new IllegalArgumentException("아이디를 입력하세요.");
        }
        if (!StringUtils.hasText(userVO.getPassword())) {
            throw new IllegalArgumentException("패스워드를 입력하세요.");
        }
        if (!StringUtils.hasText(userVO.getUserName())) {
            throw new IllegalArgumentException("이름을 입력하세요.");
        }
    }
}
