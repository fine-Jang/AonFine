package com.aonfine.lunch.service.impl;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Resource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.aonfine.auth.service.UserVO;
import com.aonfine.lunch.service.LunchVoteService;
import com.aonfine.lunch.service.LunchVoteVO;

@Service("lunchVoteService")
public class LunchVoteServiceImpl implements LunchVoteService {

    @Resource(name = "lunchVoteMapper")
    private LunchVoteMapper lunchVoteMapper;

    private final Random random = new Random();

    public List<LunchVoteVO> selectTodayTopList(int limit) {
        return lunchVoteMapper.selectTodayTopList(limit);
    }

    public List<LunchVoteVO> selectTodayBoardList() {
        return lunchVoteMapper.selectTodayBoardList();
    }

    public List<LunchVoteVO> selectMonthlyWinnerList(String month) {
        if (month == null || month.trim().length() == 0) {
            throw new IllegalArgumentException("조회할 월을 입력하세요.");
        }
        YearMonth selectedMonth;
        try {
            selectedMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("조회할 월 형식이 올바르지 않습니다.", e);
        }

        String startDate = selectedMonth.atDay(1).toString();
        String endDate = selectedMonth.plusMonths(1).atDay(1).toString();
        List<LunchVoteVO> results = lunchVoteMapper.selectMonthlyVoteResultList(startDate, endDate);
        List<LunchVoteVO> winners = new ArrayList<LunchVoteVO>();
        String winnerDate = null;
        for (LunchVoteVO result : results) {
            if (!result.getVoteDate().equals(winnerDate)) {
                winners.add(result);
                winnerDate = result.getVoteDate();
            }
        }
        return winners;
    }

    public LunchVoteVO selectTodayMyVote(String userId) {
        if (userId == null || userId.trim().length() == 0) {
            return null;
        }
        return lunchVoteMapper.selectTodayMyVote(userId);
    }

    public LunchVoteVO vote(Integer restaurantId, UserVO loginUser) {
        validateLogin(loginUser);
        if (restaurantId == null) {
            throw new IllegalArgumentException("투표할 맛집을 선택하세요.");
        }
        if (lunchVoteMapper.selectTodayMyVote(loginUser.getUserId()) != null) {
            throw new IllegalArgumentException("오늘 점심 투표는 이미 완료했습니다.");
        }
        LunchVoteVO restaurant = lunchVoteMapper.selectRestaurant(restaurantId);
        if (restaurant == null) {
            throw new IllegalArgumentException("투표할 맛집 정보를 찾을 수 없습니다.");
        }
        insertVote(restaurantId, loginUser);
        return restaurant;
    }

    public LunchVoteVO randomVote(UserVO loginUser) {
        validateLogin(loginUser);
        if (lunchVoteMapper.selectTodayMyVote(loginUser.getUserId()) != null) {
            throw new IllegalArgumentException("오늘 점심 선택은 이미 완료했습니다.");
        }
        List<LunchVoteVO> candidates = lunchVoteMapper.selectCandidateList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("랜덤 선택할 맛집이 없습니다.");
        }
        LunchVoteVO selected = candidates.get(random.nextInt(candidates.size()));
        insertVote(selected.getRestaurantId(), loginUser);
        return selected;
    }
    public void cancelTodayVote(UserVO loginUser) {
        validateLogin(loginUser);
        int deleted = lunchVoteMapper.deleteTodayVote(loginUser.getUserId());
        if (deleted == 0) {
            throw new IllegalArgumentException("취소할 오늘 점심 투표가 없습니다.");
        }
    }

    private void insertVote(Integer restaurantId, UserVO loginUser) {
        LunchVoteVO voteVO = new LunchVoteVO();
        voteVO.setRestaurantId(restaurantId);
        voteVO.setUserId(loginUser.getUserId());
        voteVO.setUserName(loginUser.getUserName());
        try {
            lunchVoteMapper.insertVote(voteVO);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("오늘 점심 투표는 이미 완료했습니다.", e);
        }
    }

    private void validateLogin(UserVO loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new IllegalArgumentException("로그인 후 이용할 수 있습니다.");
        }
    }
}
