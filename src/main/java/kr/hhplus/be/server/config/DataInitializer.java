package kr.hhplus.be.server.config;

import kr.hhplus.be.server.domain.concert.ConcertSchedule;
import kr.hhplus.be.server.domain.concert.ConcertScheduleRepository;
import kr.hhplus.be.server.domain.concert.Seat;
import kr.hhplus.be.server.domain.concert.SeatRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Mock 데이터 초기화
 */
@Configuration
@Profile({"local", "dev"})
public class DataInitializer {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    CommandLineRunner initData(
            ConcertScheduleRepository scheduleRepository,
            SeatRepository seatRepository) {
        return args -> {
            // Mock 공연 일정 3개 생성
            LocalDate today = LocalDate.now();
            for (int i = 1; i <= 3; i++) {
                LocalDate concertDate = today.plusDays(i);
                
                if (scheduleRepository.findByConcertDate(concertDate).isEmpty()) {
                    ConcertSchedule schedule = new ConcertSchedule(concertDate);
                    scheduleRepository.save(schedule);
                    
                    // 각 일정마다 50개 좌석 생성
                    String dateStr = concertDate.format(formatter);
                    for (int seatNum = 1; seatNum <= 50; seatNum++) {
                        Seat seat = new Seat(dateStr, seatNum);
                        seatRepository.save(seat);
                    }
                }
            }
            
            System.out.println("✅ Mock 데이터 초기화 완료");
        };
    }
}
