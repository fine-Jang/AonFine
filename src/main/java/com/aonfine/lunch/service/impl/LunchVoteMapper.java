package com.aonfine.lunch.service.impl;

import java.util.List;

import com.aonfine.lunch.service.LunchVoteVO;

public interface LunchVoteMapper {
    List<LunchVoteVO> selectTodayTopList(int limit);

    List<LunchVoteVO> selectTodayBoardList();

    List<LunchVoteVO> selectCandidateList();

    LunchVoteVO selectTodayMyVote(String userId);

    LunchVoteVO selectRestaurant(Integer restaurantId);

    void insertVote(LunchVoteVO voteVO);

    int deleteTodayVote(String userId);
}