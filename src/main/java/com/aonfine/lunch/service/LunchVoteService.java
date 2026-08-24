package com.aonfine.lunch.service;

import java.util.List;

import com.aonfine.auth.service.UserVO;

public interface LunchVoteService {
    List<LunchVoteVO> selectTodayTopList(int limit);

    List<LunchVoteVO> selectTodayBoardList();

    LunchVoteVO selectTodayMyVote(String userId);

    LunchVoteVO vote(Integer restaurantId, UserVO loginUser);

    LunchVoteVO randomVote(UserVO loginUser);

    void cancelTodayVote(UserVO loginUser);
}