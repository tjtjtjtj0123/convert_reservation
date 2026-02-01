-- 대기열 토큰 테이블
CREATE TABLE queue_token (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 고유 번호',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    token VARCHAR(100) NOT NULL UNIQUE COMMENT '대기열 토큰 UUID',
    position INT NULL COMMENT '대기 순서 (WAITING 상태일 때만)',
    status VARCHAR(20) NOT NULL COMMENT '토큰 상태 (WAITING, ACTIVE, EXPIRED)',
    expires_at DATETIME NULL COMMENT '토큰 만료 시각 (ACTIVE 상태일 때만)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    
    INDEX idx_token (token),
    INDEX idx_user_status (user_id, status),
    INDEX idx_status_position (status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='대기열 토큰';
