package vdt.mini.management_service.util.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    UNAUTHORIZED(401, "Your credentials were invalid, please check again", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(403, "You're not allowed to access this action, please try later", HttpStatus.FORBIDDEN),
    REGEX_INVALID(400, "Your regex was invalid, please try on later", HttpStatus.BAD_REQUEST),
    JWT_SIGN_ERROR(400, "There was an error when sign your token, please check out your token's format",
            HttpStatus.BAD_REQUEST),
    TOKEN_SIGNATURE_INVALID(400, "Your token signature was invalid, please login again to get your new access token",
            HttpStatus.BAD_REQUEST),
    TOKEN_DISABLED(401, "Your token is disabled", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(401, "Your token was expired, please login again", HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_EXIST(401, "Can't find any account with your given email, please try others", HttpStatus.UNAUTHORIZED),
    PASSWORD_INVALID(401, "Your password was invalid, please try again", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(401, "Your token type was not allowed, please try again with others", HttpStatus.UNAUTHORIZED),
    SERVICE_NOT_FOUND(404, "Service not found", HttpStatus.NOT_FOUND),
    INBOUND_ENDPOINT_NOT_FOUND(404, "Inbound endpoint not found", HttpStatus.NOT_FOUND),
    OUTBOUND_ENDPOINT_NOT_FOUND(404, "Outbound endpoint not found", HttpStatus.NOT_FOUND),
    INVALID_INPUT(400, "Invalid input value", HttpStatus.BAD_REQUEST),;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
