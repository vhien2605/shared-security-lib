package vdt.mini.management_service.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.AuthenticationException;
import vdt.mini.management_service.util.enums.ErrorCode;

@Setter
@Getter
public class AuthException extends AuthenticationException {
    private final ErrorCode errorCode;

    public AuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
