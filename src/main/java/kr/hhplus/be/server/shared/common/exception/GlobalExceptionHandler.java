package kr.hhplus.be.server.shared.common.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        ProblemDetail problemDetail = new ProblemDetail(
                "https://api.concert.com/problems/" + ex.getErrorCode(),
                ex.getClass().getSimpleName(),
                ex.getHttpStatus(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(problemDetail);
    }

    /**
     * 동시성 충돌 예외 처리 (낙관적 락)
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(Exception ex) {
        ProblemDetail problemDetail = new ProblemDetail(
                "https://api.concert.com/problems/concurrent-modification",
                "Concurrent Modification",
                409,
                "다른 사용자가 동시에 같은 리소스를 수정했습니다. 다시 시도해주세요.",
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex) {
        ProblemDetail problemDetail = new ProblemDetail(
                "https://api.concert.com/problems/internal-error",
                "Internal Server Error",
                500,
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
