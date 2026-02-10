package kr.hhplus.be.server.shared.common.exception;

import java.time.LocalDateTime;

/**
 * RFC 7807 Problem Details for HTTP APIs
 * 표준 에러 응답 형식
 */
public class ProblemDetail {
    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;
    private LocalDateTime timestamp;

    public ProblemDetail() {
    }

    public ProblemDetail(String type, String title, Integer status, String detail, String instance) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.timestamp = LocalDateTime.now();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
