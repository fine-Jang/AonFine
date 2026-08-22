package com.aonfine.lunch.service;

import java.io.Serializable;

public class LunchCalendarDayVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String date;
    private int dayOfMonth;
    private boolean currentMonth;
    private boolean today;
    private LunchVoteVO winner;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public boolean isCurrentMonth() {
        return currentMonth;
    }

    public void setCurrentMonth(boolean currentMonth) {
        this.currentMonth = currentMonth;
    }

    public boolean isToday() {
        return today;
    }

    public void setToday(boolean today) {
        this.today = today;
    }

    public LunchVoteVO getWinner() {
        return winner;
    }

    public void setWinner(LunchVoteVO winner) {
        this.winner = winner;
    }
}
