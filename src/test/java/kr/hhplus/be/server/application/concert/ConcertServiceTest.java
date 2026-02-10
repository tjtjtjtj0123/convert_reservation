package kr.hhplus.be.server.application.concert;

import kr.hhplus.be.server.concert.application.service.ConcertService;
import kr.hhplus.be.server.concert.domain.model.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.concert.interfaces.api.dto.AvailableDatesResponse;
import kr.hhplus.be.server.concert.interfaces.api.dto.SeatListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("공연 서비스 단위 테스트")
class ConcertServiceTest {

    @Mock
    private ConcertScheduleRepository scheduleRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ConcertService concertService;

    private ConcertSchedule schedule1;
    private ConcertSchedule schedule2;
    private Seat seat1;
    private Seat seat2;

    @BeforeEach
    void setUp() {
        schedule1 = new ConcertSchedule(LocalDate.now().plusDays(1));
        schedule2 = new ConcertSchedule(LocalDate.now().plusDays(2));

        seat1 = new Seat("2026-02-01", 1);
        seat2 = new Seat("2026-02-01", 2);
    }

    @Test
    @DisplayName("예약 가능한 날짜 목록을 조회할 수 있다")
    void getAvailableDates_Success() {
        // given
        when(scheduleRepository.findAvailableSchedules(any(LocalDate.class)))
                .thenReturn(Arrays.asList(schedule1, schedule2));

        // when
        AvailableDatesResponse response = concertService.getAvailableDates();

        // then
        assertThat(response.getDates()).hasSize(2);
        verify(scheduleRepository, times(1)).findAvailableSchedules(any(LocalDate.class));
    }

    @Test
    @DisplayName("특정 날짜의 좌석 목록을 조회할 수 있다")
    void getSeats_Success() {
        // given
        String date = "2026-02-01";
        when(seatRepository.findByConcertDateOrderBySeatNumber(date))
                .thenReturn(Arrays.asList(seat1, seat2));

        // when
        SeatListResponse response = concertService.getSeats(date);

        // then
        assertThat(response.getDate()).isEqualTo(date);
        assertThat(response.getSeats()).hasSize(2);
        assertThat(response.getSeats().get(0).getSeatNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("좌석이 없는 날짜 조회 시 좌석을 초기화한다")
    void getSeats_InitializeWhenEmpty() {
        // given
        String date = "2026-02-03";
        when(seatRepository.findByConcertDateOrderBySeatNumber(date))
                .thenReturn(Collections.emptyList());
        when(seatRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.findByConcertDate(any(LocalDate.class)))
                .thenReturn(java.util.Optional.empty());
        when(scheduleRepository.save(any(ConcertSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        SeatListResponse response = concertService.getSeats(date);

        // then
        assertThat(response.getSeats()).hasSize(50); // 1~50 좌석 생성
        verify(seatRepository, times(1)).saveAll(anyList()); // 배치 저장 1회 호출
    }
}
