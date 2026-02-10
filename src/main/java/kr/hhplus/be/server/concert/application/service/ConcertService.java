package kr.hhplus.be.server.concert.application.service;

import kr.hhplus.be.server.concert.domain.model.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.concert.interfaces.api.dto.AvailableDatesResponse;
import kr.hhplus.be.server.concert.interfaces.api.dto.SeatListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 공연 조회 Use Case (Application Layer)
 * 도메인 기반 클린 아키텍처
 */
@Service
@Transactional(readOnly = true)
public class ConcertService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final ConcertScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;

    public ConcertService(ConcertScheduleRepository scheduleRepository, SeatRepository seatRepository) {
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * 예약 가능한 날짜 목록 조회
     */
    public AvailableDatesResponse getAvailableDates() {
        List<ConcertSchedule> schedules = scheduleRepository.findAvailableSchedules(LocalDate.now());
        
        List<String> dates = schedules.stream()
                .map(schedule -> schedule.getConcertDate().format(DATE_FORMATTER))
                .toList();
        
        return new AvailableDatesResponse(dates);
    }

    /**
     * 특정 날짜의 좌석 목록 조회
     */
    public SeatListResponse getSeats(String date) {
        List<Seat> seats = seatRepository.findByConcertDateOrderBySeatNumber(date);
        
        // 좌석이 없으면 초기화 (Mock 데이터 생성)
        if (seats.isEmpty()) {
            seats = initializeSeatsForDate(date);
        }
        
        List<kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus> seatStatusList = seats.stream()
                .map(this::toSeatStatusDto)
                .toList();
        
        return new SeatListResponse(date, seatStatusList);
    }
    
    /**
     * Seat 엔티티를 DTO로 변환
     */
    private kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus toSeatStatusDto(Seat seat) {
        return new kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus(
                seat.getSeatNumber(),
                mapStatus(seat.getStatus()),
                seat.getReservedUserId(),
                seat.getReservedUntil()
        );
    }

    /**
     * 날짜에 대한 좌석 초기화 (Mock)
     */
    @Transactional
    public List<Seat> initializeSeatsForDate(String date) {
        List<Seat> seats = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Seat seat = new Seat(date, i);
            seats.add(seat);
        }
        
        // saveAll로 배치 저장
        seats = seatRepository.saveAll(seats);
        
        // 스케줄도 생성
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        scheduleRepository.findByConcertDate(localDate)
                .orElseGet(() -> scheduleRepository.save(new ConcertSchedule(localDate)));
        
        return seats;
    }

    private kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus.SeatStatusEnum mapStatus(SeatStatus status) {
        return switch (status) {
            case AVAILABLE -> kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus.SeatStatusEnum.AVAILABLE;
            case TEMP_HELD -> kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus.SeatStatusEnum.TEMP_HELD;
            case RESERVED -> kr.hhplus.be.server.concert.interfaces.api.dto.SeatStatus.SeatStatusEnum.RESERVED;
        };
    }
}
