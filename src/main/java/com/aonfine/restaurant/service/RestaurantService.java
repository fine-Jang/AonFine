package com.aonfine.restaurant.service;

import java.util.List;

public interface RestaurantService {
    List<RestaurantVO> selectRestaurantList(RestaurantSearchVO searchVO);

    int selectRestaurantListTotCnt(RestaurantSearchVO searchVO);

    List<RestaurantVO> selectLatestRestaurantList(int limit);

    RestaurantVO selectRestaurant(Integer restaurantId);

    void insertRestaurant(RestaurantVO restaurantVO);

    void updateRestaurant(RestaurantVO restaurantVO);

    void deleteRestaurant(Integer restaurantId);
}
