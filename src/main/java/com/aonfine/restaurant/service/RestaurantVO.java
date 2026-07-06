package com.aonfine.restaurant.service;

import java.math.BigDecimal;

public class RestaurantVO {
    private Integer restaurantId;
    private String restaurantName;
    private String storeName;
    private String imagePath;
    private String imageOriginalName;
    private String menuName;
    private String categoryCode;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String regDt;
    private String modDt;

    public Integer getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Integer restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageOriginalName() {
        return imageOriginalName;
    }

    public void setImageOriginalName(String imageOriginalName) {
        this.imageOriginalName = imageOriginalName;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        if ("KOREAN".equals(categoryCode)) {
            return "\uD55C\uC2DD";
        }
        if ("CHINESE".equals(categoryCode)) {
            return "\uC911\uC2DD";
        }
        if ("JAPANESE".equals(categoryCode)) {
            return "\uC77C\uC2DD";
        }
        if ("WESTERN".equals(categoryCode)) {
            return "\uC591\uC2DD";
        }
        if ("SNACK".equals(categoryCode)) {
            return "\uBD84\uC2DD";
        }
        if ("CAFE".equals(categoryCode)) {
            return "\uCE74\uD398";
        }
        return "\uAE30\uD0C0";
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRegDt() {
        return regDt;
    }

    public void setRegDt(String regDt) {
        this.regDt = regDt;
    }

    public String getModDt() {
        return modDt;
    }

    public void setModDt(String modDt) {
        this.modDt = modDt;
    }
}
