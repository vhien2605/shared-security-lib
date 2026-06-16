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
    INVALID_INPUT(400, "Invalid input value", HttpStatus.BAD_REQUEST),
    ENDPOINT_NOT_DISCOVERED(409, "Endpoint is no longer discovered from code and cannot be activated", HttpStatus.CONFLICT),
    SETTING_TEMPLATE_NOT_FOUND(404, "Setting template not found", HttpStatus.NOT_FOUND),
    GLOBAL_TEMPLATE_NOT_FOUND(404, "Global template not found", HttpStatus.NOT_FOUND),
    SERVICE_TEMPLATE_NOT_FOUND(404, "Service template not found", HttpStatus.NOT_FOUND),
    SETTING_TEMPLATE_VERSION_CONFLICT(409, "Template version conflict", HttpStatus.CONFLICT),
    INVALID_SETTING_TEMPLATE(400, "Invalid setting template", HttpStatus.BAD_REQUEST),
    BATCH_APPLY_FAILED(500, "Batch apply failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CLIENT_NOT_FOUND(404, "Client not found", HttpStatus.NOT_FOUND),
    CLIENT_CODE_ALREADY_EXISTS(409, "Client code already exists", HttpStatus.CONFLICT),
    INVALID_CLIENT_STATUS_TRANSITION(400, "Invalid client status transition", HttpStatus.BAD_REQUEST),
    AUTH_CONFIG_NOT_FOUND(404, "Auth config not found", HttpStatus.NOT_FOUND),
    AUTH_CONFIG_CONFLICT(409, "Auth config conflict", HttpStatus.CONFLICT),
    ACCESS_RULE_NOT_FOUND(404, "Access rule not found", HttpStatus.NOT_FOUND),
    ACCESS_PERMISSION_NOT_FOUND(404, "Access permission not found", HttpStatus.NOT_FOUND),
    ACCESS_PERMISSION_CONFLICT(409, "Access permission conflict", HttpStatus.CONFLICT),
    CREDENTIAL_GENERATION_FAILED(500, "Credential generation failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
