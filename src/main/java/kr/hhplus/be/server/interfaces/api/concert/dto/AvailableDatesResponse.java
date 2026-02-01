package kr.hhplus.be.server.interfaces.api.concert.dto;

import java.util.List;

/**
 * 예약 가능한 날짜 응답 DTO
 */
public class AvailableDatesResponse {
    private List<String> dates;

    public AvailableDatesResponse() {
    }

    public AvailableDatesResponse(List<String> dates) {
        this.dates = dates;
    }

    public List<String> getDates() {
        return dates;
    }

    public void setDates(List<String> dates) {
        this.dates = dates;
    }
}
