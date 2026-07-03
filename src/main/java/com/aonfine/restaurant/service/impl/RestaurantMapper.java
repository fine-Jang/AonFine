package com.aonfine.restaurant.service.impl;

import java.util.List;

import com.aonfine.restaurant.service.RestaurantSearchVO;
import com.aonfine.restaurant.service.RestaurantVO;

public interface RestaurantMapper {
    List<RestaurantVO> selectRestaurantList(RestaurantSearchVO searchVO);

    int selectRestaurantListTotCnt(RestaurantSearchVO searchVO);

    List<RestaurantVO> selectLatestRestaurantList(int limit);

    RestaurantVO selectRestaurant(Integer restaurantId);

    void insertRestaurant(RestaurantVO restaurantVO);

    void updateRestaurant(RestaurantVO restaurantVO);

    void deleteRestaurant(Integer restaurantId);
}
