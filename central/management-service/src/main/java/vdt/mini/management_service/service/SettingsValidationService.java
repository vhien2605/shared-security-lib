package vdt.mini.management_service.service;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.request.SettingTemplateUpdateRequest;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.AlertSeverity;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.util.enums.EndpointType;
import vdt.mini.management_service.util.enums.RollbackStrategy;

import java.util.List;

@Service
public class SettingsValidationService {
    public void validateTemplateUpdate(SettingTemplateUpdateRequest request) {
        if (request.getInboundRateLimit() == null || request.getInboundRateLimit() < 0) throw invalid("inboundRateLimit must be >= 0");
        if (request.getInboundRateLimitWindowSeconds() == null || request.getInboundRateLimitWindowSeconds() <= 0) throw invalid("inboundRateLimitWindowSeconds must be > 0");
        if (request.getInboundTimeoutMs() == null || request.getInboundTimeoutMs() <= 0) throw invalid("inboundTimeoutMs must be > 0");
        if (request.getInboundRequestSizeLimitKb() == null || request.getInboundRequestSizeLimitKb() <= 0) throw invalid("inboundRequestSizeLimitKb must be > 0");
        if (request.getInboundResponseSizeLimitKb() == null || request.getInboundResponseSizeLimitKb() <= 0) throw invalid("inboundResponseSizeLimitKb must be > 0");
        if (request.getInboundResponseTimeThresholdMs() == null || request.getInboundResponseTimeThresholdMs() <= 0) throw invalid("inboundResponseTimeThresholdMs must be > 0");
        if (request.getInboundLogRetentionDays() == null || request.getInboundLogRetentionDays() <= 0) throw invalid("inboundLogRetentionDays must be > 0");
        if (request.getOutboundTimeoutMs() == null || request.getOutboundTimeoutMs() <= 0) throw invalid("outboundTimeoutMs must be > 0");
        if (request.getOutboundRetryCount() == null || request.getOutboundRetryCount() < 0) throw invalid("outboundRetryCount must be >= 0");
        if (request.getOutboundRetryBackoffMs() == null || request.getOutboundRetryBackoffMs() <= 0) throw invalid("outboundRetryBackoffMs must be > 0");
        if (request.getOutboundResponseTimeThresholdMs() == null || request.getOutboundResponseTimeThresholdMs() <= 0) throw invalid("outboundResponseTimeThresholdMs must be > 0");
        if (request.getOutboundLogRetentionDays() == null || request.getOutboundLogRetentionDays() <= 0) throw invalid("outboundLogRetentionDays must be > 0");
        if (request.getAlertThrottleMinutes() == null || request.getAlertThrottleMinutes() <= 0) throw invalid("alertThrottleMinutes must be > 0");
        if (request.getAlertChannels() == null || request.getAlertChannels().isEmpty()) throw invalid("alertChannels must not be empty");
        try { AlertSeverity.valueOf(request.getAlertSeverity()); } catch (Exception e) { throw invalid("invalid alertSeverity"); }
        try { RollbackStrategy.valueOf(request.getOutboundRollbackStrategy()); } catch (Exception e) { throw invalid("invalid outboundRollbackStrategy"); }
    }

    public List<EndpointType> parseEndpointTypes(List<String> endpointTypes) {
        if (endpointTypes == null || endpointTypes.isEmpty()) return List.of(EndpointType.INBOUND, EndpointType.OUTBOUND);
        try {
            return endpointTypes.stream().map(String::toUpperCase).map(EndpointType::valueOf).distinct().toList();
        } catch (Exception e) {
            throw invalid("invalid endpointTypes");
        }
    }

    private AppException invalid(String message) {
        return new AppException(ErrorCode.INVALID_SETTING_TEMPLATE, message);
    }
}
