package com.aonfine.comment.service.impl;

import java.util.List;

import com.aonfine.comment.service.RestaurantCommentVO;

public interface RestaurantCommentMapper {
    List<RestaurantCommentVO> selectCommentList(Integer restaurantId);

    void insertComment(RestaurantCommentVO commentVO);

    void deleteComment(Integer commentId, String userId);
}