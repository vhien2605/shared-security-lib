package vdt.mini.management_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import vdt.mini.management_service.dto.response.ApiErrorResponse;
import vdt.mini.management_service.dto.response.ApiResponse;
import vdt.mini.management_service.util.enums.ErrorCode;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler({AppException.class})
    public ApiResponse handleAppException(AppException e, WebRequest request) {
        log.info("---------------------------Application exception handler start---------------------------");
        String error = e.getMessage();
        return ApiErrorResponse.builder()
                .status(e.getErrorCode().getCode())
                .message(e.getErrorCode().getMessage())
                .error(e.getErrorCode().name())
                .path(request.getDescription(false))
                .build();
    }

    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler({AuthException.class})
    public ApiResponse handleAuthException(AuthException e, WebRequest request) {
        log.info("---------------------------Auth exception handler start---------------------------");
        String error = e.getMessage();
        return ApiErrorResponse.builder()
                .status(e.getErrorCode().getCode())
                .message(e.getErrorCode().getMessage())
                .error(e.getErrorCode().name())
                .path(request.getDescription(false))
                .build();
    }

    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler({AccessDeniedException.class})
    public ApiResponse handleAccessDeniedHandler(AccessDeniedException e, WebRequest request) {
        log.info("---------------------------Access denied exception handler start---------------------------");
        String error = e.getMessage();
        return ApiErrorResponse.builder()
                .status(ErrorCode.ACCESS_DENIED.getCode())
                .message(ErrorCode.ACCESS_DENIED.getMessage())
                .error(ErrorCode.ACCESS_DENIED.name())
                .path(request.getDescription(false))
                .build();
    }

//    @ResponseStatus(HttpStatus.OK)
//    @ExceptionHandler({Exception.class})
//    public ApiResponse handleServerError(Exception e, WebRequest request) throws AccessDeniedException {
//        log.info("---------------------------Server error 500 exception handler start---------------------------");
//        log.error(e.getMessage());
//        String error = e.getMessage();
//        return ApiErrorResponse.builder()
//                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
//                .message(e.getMessage())
//                .error(e.getMessage())
//                .path(request.getDescription(false))
//                .build();
//    }
}
