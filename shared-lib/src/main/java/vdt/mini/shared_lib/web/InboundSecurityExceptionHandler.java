package vdt.mini.shared_lib.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.security.SecurityStatusMapper;

import java.util.Map;

@RestControllerAdvice
public class InboundSecurityExceptionHandler {
    private final SecurityStatusMapper statusMapper;

    public InboundSecurityExceptionHandler(SecurityStatusMapper statusMapper) {
        this.statusMapper = statusMapper;
    }

    @ExceptionHandler(InboundSecurityException.class)
    public ResponseEntity<Map<String, Object>> handle(InboundSecurityException ex) {
        return ResponseEntity.status(statusMapper.toHttpStatus(ex.getErrorCode()))
                .body(Map.of(
                        "status", SecurityResultStatus.DENIED.name(),
                        "resultCode", statusMapper.resultCode(ex.getErrorCode()),
                        "errorCode", ex.getErrorCode().name(),
                        "message", ex.getMessage()));
    }
}
