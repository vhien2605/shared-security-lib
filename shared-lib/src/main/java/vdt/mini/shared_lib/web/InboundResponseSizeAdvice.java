package vdt.mini.shared_lib.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

@ControllerAdvice
public class InboundResponseSizeAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger auditLogger;

    public InboundResponseSizeAdvice(ObjectMapper objectMapper, SecurityAuditLogger auditLogger) {
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return SecurityRequestContextHolder.get() != null;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        SecurityRequestContext context = SecurityRequestContextHolder.get();
        if (context == null || context.getInboundSettings() == null || body == null) {
            return body;
        }
        InboundSettingsDTO settings = context.getInboundSettings();
        Integer limitKb = settings.getResponseSizeLimitKb();
        if (limitKb == null || limitKb <= 0) {
            return body;
        }
        long size = estimateSize(body);
        context.setResponseSizeBytes(size);
        if (size > limitKb * 1024L) {
            response.setStatusCode(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
            auditLogger.log(context, SecurityResultStatus.DENIED, SecurityErrorCode.RESPONSE_SIZE_EXCEEDED);
            return java.util.Map.of(
                    "status", SecurityResultStatus.DENIED.name(),
                    "resultCode", "413",
                    "errorCode", SecurityErrorCode.RESPONSE_SIZE_EXCEEDED.name(),
                    "message", "Response size exceeded");
        }
        return body;
    }

    private long estimateSize(Object body) {
        if (body instanceof byte[] bytes) {
            return bytes.length;
        }
        if (body instanceof String value) {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        try {
            return objectMapper.writeValueAsBytes(body).length;
        } catch (JsonProcessingException ex) {
            return 0L;
        }
    }
}
