package kr.hhplus.be.server.payment.infrastructure.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 데이터 플랫폼 전송 서비스 (Mock)
 *
 * 실제 구현에서는 외부 데이터 플랫폼 API를 호출하지만,
 * 현재는 로그를 남기는 Mock으로 구현합니다.
 */
@Service
public class DataPlatformSendService {

    private static final Logger log = LoggerFactory.getLogger(DataPlatformSendService.class);

    /**
     * 결제 완료 정보를 데이터 플랫폼에 전송 (Mock)
     *
     * @param paymentId    결제 ID
     * @param userId       사용자 ID
     * @param concertDate  콘서트 날짜
     * @param seatNumber   좌석 번호
     * @param amount       결제 금액
     */
    public void sendPaymentData(Long paymentId, String userId, String concertDate,
                                 Integer seatNumber, Long amount) {
        log.info("[DataPlatform] 결제 데이터 전송 시작 - paymentId={}, userId={}, date={}, seat={}, amount={}",
                paymentId, userId, concertDate, seatNumber, amount);

        // Mock: 실제로는 HTTP API 호출
        // ex) restTemplate.postForEntity(dataPlatformUrl, request, Void.class);

        log.info("[DataPlatform] 결제 데이터 전송 완료 - paymentId={}", paymentId);
    }

    /**
     * 예약 완료 정보를 데이터 플랫폼에 전송 (Mock)
     *
     * @param reservationId 예약 ID
     * @param userId        사용자 ID
     * @param concertDate   콘서트 날짜
     * @param seatNumber    좌석 번호
     */
    public void sendReservationData(Long reservationId, String userId, String concertDate,
                                     Integer seatNumber) {
        log.info("[DataPlatform] 예약 데이터 전송 시작 - reservationId={}, userId={}, date={}, seat={}",
                reservationId, userId, concertDate, seatNumber);

        // Mock: 실제로는 HTTP API 호출

        log.info("[DataPlatform] 예약 데이터 전송 완료 - reservationId={}", reservationId);
    }
}
