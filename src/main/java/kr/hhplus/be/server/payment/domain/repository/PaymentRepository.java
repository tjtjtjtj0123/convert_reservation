package kr.hhplus.be.server.payment.domain.repository;

import kr.hhplus.be.server.payment.domain.model.Payment;

import java.util.Optional;

/**
 * 결제 리포지토리 인터페이스 (Domain Layer)
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface PaymentRepository {
    
    /**
     * 결제 저장
     */
    Payment save(Payment payment);
    
    /**
     * ID로 결제 조회
     */
    Optional<Payment> findById(Long id);
    
    /**
     * 예약 ID로 결제 조회
     */
    Optional<Payment> findByReservationId(Long reservationId);
}
