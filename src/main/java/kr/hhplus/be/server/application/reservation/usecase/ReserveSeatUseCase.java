package kr.hhplus.be.server.application.reservation.usecase;

import kr.hhplus.be.server.interfaces.api.reservation.dto.SeatReserveRequest;
import kr.hhplus.be.server.interfaces.api.reservation.dto.SeatReserveResponse;

/**
 * 좌석 예약 유스케이스 인터페이스 (클린 아키텍처)
 */
public interface ReserveSeatUseCase {
    SeatReserveResponse execute(SeatReserveRequest request, String queueToken);
}
