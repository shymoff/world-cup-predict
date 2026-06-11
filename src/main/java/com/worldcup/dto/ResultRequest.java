package com.worldcup.dto;

/**
 * Cialo zadania PUT przy zapisie wyniku meczu.
 * score1/score2 = null oznacza wyczyszczenie wyniku.
 */
public class ResultRequest {

    private Integer score1;
    private Integer score2;

    public Integer getScore1() {
        return score1;
    }

    public void setScore1(Integer score1) {
        this.score1 = score1;
    }

    public Integer getScore2() {
        return score2;
    }

    public void setScore2(Integer score2) {
        this.score2 = score2;
    }
}
