package com.aonfine.comment.service.impl;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aonfine.comment.service.RestaurantCommentService;
import com.aonfine.comment.service.RestaurantCommentVO;

@Service("restaurantCommentService")
public class RestaurantCommentServiceImpl implements RestaurantCommentService {

    @Resource(name = "restaurantCommentMapper")
    private RestaurantCommentMapper restaurantCommentMapper;

    public List<RestaurantCommentVO> selectCommentList(Integer restaurantId) {
        return restaurantCommentMapper.selectCommentList(restaurantId);
    }

    public void insertComment(RestaurantCommentVO commentVO) {
        if (commentVO == null || commentVO.getRestaurantId() == null) {
            throw new IllegalArgumentException("맛집 정보가 올바르지 않습니다.");
        }
        if (!StringUtils.hasText(commentVO.getUserId()) || !StringUtils.hasText(commentVO.getUserName())) {
            throw new IllegalArgumentException("로그인 정보가 올바르지 않습니다.");
        }
        if (!StringUtils.hasText(commentVO.getCommentText())) {
            throw new IllegalArgumentException("댓글 내용을 입력하세요.");
        }
        restaurantCommentMapper.insertComment(commentVO);
    }

    public void deleteComment(Integer commentId, String userId) {
        if (commentId == null || !StringUtils.hasText(userId)) {
            return;
        }
        restaurantCommentMapper.deleteComment(commentId, userId);
    }
}