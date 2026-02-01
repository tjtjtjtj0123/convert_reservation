-- =====================================================
-- Concert Booking Service - Database Schema (DDL)
-- MySQL 8.0
-- =====================================================

-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS hhplus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hhplus;

-- =====================================================
-- 1. 공연 일정 테이블 (concert_schedule)
-- =====================================================
CREATE TABLE concert_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '공연 일정 ID',
    concert_date DATE NOT NULL UNIQUE COMMENT '공연 날짜',
    total_seats INT NOT NULL DEFAULT 50 COMMENT '총 좌석 수',
    available_seats INT NOT NULL DEFAULT 50 COMMENT '예약 가능 좌석 수',
    
    INDEX idx_concert_date (concert_date),
    INDEX idx_available_seats (available_seats)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공연 일정';

-- =====================================================
-- 2. 좌석 테이블 (seat)
-- =====================================================
CREATE TABLE seat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '좌석 ID',
    concert_date VARCHAR(20) NOT NULL COMMENT '공연 날짜 (YYYY-MM-DD)',
    seat_number INT NOT NULL COMMENT '좌석 번호 (1-50)',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '좌석 상태: AVAILABLE, TEMP_HELD, RESERVED',
    reserved_user_id VARCHAR(100) COMMENT '예약자 사용자 ID',
    reserved_until DATETIME COMMENT '임시 배정 만료 시각',
    version BIGINT DEFAULT 0 COMMENT '낙관적 락 버전',
    
    UNIQUE KEY uk_seat_date_number (concert_date, seat_number),
    INDEX idx_concert_date (concert_date),
    INDEX idx_status (status),
    INDEX idx_reserved_until (reserved_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='좌석';

-- =====================================================
-- 3. 예약 테이블 (reservation)
-- =====================================================
CREATE TABLE reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 ID',
    user_id VARCHAR(100) NOT NULL COMMENT '사용자 ID',
    seat_id BIGINT NOT NULL COMMENT '좌석 ID (FK)',
    concert_date VARCHAR(20) NOT NULL COMMENT '공연 날짜',
    seat_number INT NOT NULL COMMENT '좌석 번호',
    price BIGINT NOT NULL COMMENT '예약 금액',
    status VARCHAR(20) NOT NULL COMMENT '예약 상태: TEMP_HELD, CONFIRMED, CANCELLED, EXPIRED',
    reserved_at DATETIME NOT NULL COMMENT '예약 생성 시각',
    reserved_until DATETIME COMMENT '임시 예약 만료 시각',
    confirmed_at DATETIME COMMENT '예약 확정 시각',
    
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_seat_id (seat_id),
    INDEX idx_reserved_until (reserved_until),
    INDEX idx_user_date_seat_status (user_id, concert_date, seat_number, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='예약';

-- =====================================================
-- 4. 결제 테이블 (payment)
-- =====================================================
CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID',
    reservation_id BIGINT NOT NULL COMMENT '예약 ID (FK)',
    user_id VARCHAR(100) NOT NULL COMMENT '사용자 ID',
    amount BIGINT NOT NULL COMMENT '결제 금액',
    status VARCHAR(20) NOT NULL COMMENT '결제 상태: COMPLETED, CANCELLED, FAILED',
    paid_at DATETIME COMMENT '결제 완료 시각',
    
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_paid_at (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제';

-- =====================================================
-- 5. 포인트 잔액 테이블 (point_balance)
-- =====================================================
CREATE TABLE point_balance (
    user_id VARCHAR(100) PRIMARY KEY COMMENT '사용자 ID (PK)',
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '포인트 잔액',
    version BIGINT DEFAULT 0 COMMENT '낙관적 락 버전'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='포인트 잔액';

-- =====================================================
-- 6. 대기열 토큰 테이블 (queue_token)
-- =====================================================
CREATE TABLE queue_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 ID',
    user_id VARCHAR(100) NOT NULL COMMENT '사용자 ID',
    token VARCHAR(100) NOT NULL UNIQUE COMMENT 'UUID 토큰 값',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '토큰 상태: WAITING, ACTIVE, EXPIRED',
    position INT COMMENT '대기 순서 (0=활성화됨)',
    expires_at DATETIME COMMENT '토큰 만료 시각',
    created_at DATETIME NOT NULL COMMENT '토큰 생성 시각',
    
    UNIQUE KEY uk_token (token),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_status_created (status, created_at),
    INDEX idx_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='대기열 토큰';


-- =====================================================
-- 초기 데이터 (선택사항)
-- =====================================================

-- 향후 3일간 공연 일정 생성
INSERT INTO concert_schedule (concert_date, total_seats, available_seats) VALUES
(CURDATE() + INTERVAL 1 DAY, 50, 50),
(CURDATE() + INTERVAL 2 DAY, 50, 50),
(CURDATE() + INTERVAL 3 DAY, 50, 50);

-- 각 공연 날짜별 좌석 50개 생성 (예시: 첫 번째 날짜)
-- 실제 데이터는 DataInitializer에서 생성됨
