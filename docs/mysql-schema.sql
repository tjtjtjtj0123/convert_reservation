-- MySQL 8.0 테이블 생성 스크립트
-- JPA 엔티티 기반 스키마
-- 생성일: 2026-02-07
-- DB: hhplus (docker-compose MySQL 8.0)

USE hhplus;

-- 기존 테이블 삭제 (역순으로 삭제 - 의존성 고려)
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS concert_schedule;
DROP TABLE IF EXISTS point_balance;
DROP TABLE IF EXISTS queue_token;

-- ========================================
-- 1. 대기열 토큰 테이블 (queue_token)
-- ========================================
CREATE TABLE queue_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'WAITING, ACTIVE, EXPIRED',
    position INT NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_queue_token_token (token),
    INDEX idx_queue_status (status),
    INDEX idx_queue_status_created (status, created_at),
    INDEX idx_queue_status_expires (status, expires_at),
    INDEX idx_queue_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 2. 공연 일정 테이블 (concert_schedule)
-- ========================================
CREATE TABLE concert_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    concert_date DATE NOT NULL,
    total_seats INT NOT NULL DEFAULT 50,
    available_seats INT NOT NULL DEFAULT 50,
    PRIMARY KEY (id),
    UNIQUE KEY uk_concert_date (concert_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 3. 좌석 테이블 (seat)
-- ========================================
CREATE TABLE seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    concert_date VARCHAR(50) NOT NULL,
    seat_number INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE, TEMP_HELD, RESERVED',
    reserved_user_id VARCHAR(255) NULL,
    reserved_until DATETIME(6) NULL,
    version BIGINT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seat_date_number (concert_date, seat_number),
    INDEX idx_seat_concert_date (concert_date),
    INDEX idx_seat_status (status),
    INDEX idx_seat_reserved_until (reserved_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 4. 예약 테이블 (reservation)
-- ========================================
CREATE TABLE reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    seat_id BIGINT NOT NULL,
    concert_date VARCHAR(50) NOT NULL,
    seat_number INT NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'TEMP_HELD, CONFIRMED, CANCELLED, EXPIRED',
    reserved_at DATETIME(6) NOT NULL,
    reserved_until DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_reservation_status (status),
    INDEX idx_reservation_status_until (status, reserved_until),
    INDEX idx_reservation_user_id (user_id),
    INDEX idx_reservation_seat_id (seat_id),
    INDEX idx_reservation_user_date_seat (user_id, concert_date, seat_number, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 5. 포인트 잔액 테이블 (point_balance)
-- ========================================
CREATE TABLE point_balance (
    user_id VARCHAR(255) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 6. 결제 테이블 (payment)
-- ========================================
CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'COMPLETED, CANCELLED, FAILED',
    paid_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_reservation_id (reservation_id),
    INDEX idx_payment_user_id (user_id),
    INDEX idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 샘플 데이터 삽입 (선택사항)
-- ========================================

-- 공연 일정 샘플
INSERT INTO concert_schedule (concert_date, total_seats, available_seats)
VALUES 
    ('2026-03-01', 50, 50),
    ('2026-03-02', 50, 50),
    ('2026-03-03', 50, 50);

-- 좌석 샘플 (2026-03-01 공연의 좌석 50개)
INSERT INTO seat (concert_date, seat_number, status, version) VALUES
    ('2026-03-01', 1, 'AVAILABLE', 0),
    ('2026-03-01', 2, 'AVAILABLE', 0),
    ('2026-03-01', 3, 'AVAILABLE', 0),
    ('2026-03-01', 4, 'AVAILABLE', 0),
    ('2026-03-01', 5, 'AVAILABLE', 0),
    ('2026-03-01', 6, 'AVAILABLE', 0),
    ('2026-03-01', 7, 'AVAILABLE', 0),
    ('2026-03-01', 8, 'AVAILABLE', 0),
    ('2026-03-01', 9, 'AVAILABLE', 0),
    ('2026-03-01', 10, 'AVAILABLE', 0),
    ('2026-03-01', 11, 'AVAILABLE', 0),
    ('2026-03-01', 12, 'AVAILABLE', 0),
    ('2026-03-01', 13, 'AVAILABLE', 0),
    ('2026-03-01', 14, 'AVAILABLE', 0),
    ('2026-03-01', 15, 'AVAILABLE', 0),
    ('2026-03-01', 16, 'AVAILABLE', 0),
    ('2026-03-01', 17, 'AVAILABLE', 0),
    ('2026-03-01', 18, 'AVAILABLE', 0),
    ('2026-03-01', 19, 'AVAILABLE', 0),
    ('2026-03-01', 20, 'AVAILABLE', 0),
    ('2026-03-01', 21, 'AVAILABLE', 0),
    ('2026-03-01', 22, 'AVAILABLE', 0),
    ('2026-03-01', 23, 'AVAILABLE', 0),
    ('2026-03-01', 24, 'AVAILABLE', 0),
    ('2026-03-01', 25, 'AVAILABLE', 0),
    ('2026-03-01', 26, 'AVAILABLE', 0),
    ('2026-03-01', 27, 'AVAILABLE', 0),
    ('2026-03-01', 28, 'AVAILABLE', 0),
    ('2026-03-01', 29, 'AVAILABLE', 0),
    ('2026-03-01', 30, 'AVAILABLE', 0),
    ('2026-03-01', 31, 'AVAILABLE', 0),
    ('2026-03-01', 32, 'AVAILABLE', 0),
    ('2026-03-01', 33, 'AVAILABLE', 0),
    ('2026-03-01', 34, 'AVAILABLE', 0),
    ('2026-03-01', 35, 'AVAILABLE', 0),
    ('2026-03-01', 36, 'AVAILABLE', 0),
    ('2026-03-01', 37, 'AVAILABLE', 0),
    ('2026-03-01', 38, 'AVAILABLE', 0),
    ('2026-03-01', 39, 'AVAILABLE', 0),
    ('2026-03-01', 40, 'AVAILABLE', 0),
    ('2026-03-01', 41, 'AVAILABLE', 0),
    ('2026-03-01', 42, 'AVAILABLE', 0),
    ('2026-03-01', 43, 'AVAILABLE', 0),
    ('2026-03-01', 44, 'AVAILABLE', 0),
    ('2026-03-01', 45, 'AVAILABLE', 0),
    ('2026-03-01', 46, 'AVAILABLE', 0),
    ('2026-03-01', 47, 'AVAILABLE', 0),
    ('2026-03-01', 48, 'AVAILABLE', 0),
    ('2026-03-01', 49, 'AVAILABLE', 0),
    ('2026-03-01', 50, 'AVAILABLE', 0);
