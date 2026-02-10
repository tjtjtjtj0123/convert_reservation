package kr.hhplus.be.server.payment.infrastructure.persistence;

import kr.hhplus.be.server.payment.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 결제 JPA Repository (Infrastructure Layer)
 */
public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    
    /**
     * 예약 ID로 결제 조회
     */
    Optional<Payment> findByReservationId(Long reservationId);
}
