package com.aonfine.restaurant.service.impl;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.aonfine.restaurant.service.RestaurantSearchVO;
import com.aonfine.restaurant.service.RestaurantService;
import com.aonfine.restaurant.service.RestaurantVO;

@Service("restaurantService")
public class RestaurantServiceImpl implements RestaurantService {

    @Resource(name = "restaurantMapper")
    private RestaurantMapper restaurantMapper;

    public List<RestaurantVO> selectRestaurantList(RestaurantSearchVO searchVO) {
        return restaurantMapper.selectRestaurantList(searchVO);
    }

    public int selectRestaurantListTotCnt(RestaurantSearchVO searchVO) {
        return restaurantMapper.selectRestaurantListTotCnt(searchVO);
    }

    public List<RestaurantVO> selectLatestRestaurantList(int limit) {
        return restaurantMapper.selectLatestRestaurantList(limit);
    }

    public RestaurantVO selectRestaurant(Integer restaurantId) {
        return restaurantMapper.selectRestaurant(restaurantId);
    }

    public void insertRestaurant(RestaurantVO restaurantVO) {
        restaurantMapper.insertRestaurant(restaurantVO);
    }

    public void updateRestaurant(RestaurantVO restaurantVO) {
        restaurantMapper.updateRestaurant(restaurantVO);
    }

    public void deleteRestaurant(Integer restaurantId) {
        restaurantMapper.deleteRestaurant(restaurantId);
    }
}
