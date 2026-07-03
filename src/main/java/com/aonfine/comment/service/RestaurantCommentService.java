package com.aonfine.comment.service;

import java.util.List;

public interface RestaurantCommentService {
    List<RestaurantCommentVO> selectCommentList(Integer restaurantId);

    void insertComment(RestaurantCommentVO commentVO);

    void deleteComment(Integer commentId, String userId);
}