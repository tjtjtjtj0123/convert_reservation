package kr.hhplus.be.server.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 결제 리포지토리
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
