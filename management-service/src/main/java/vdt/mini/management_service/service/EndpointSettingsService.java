package vdt.mini.management_service.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.InboundSettingsPatchRequest;
import vdt.mini.management_service.dto.request.OutboundSettingsPatchRequest;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.repository.AlertConfigRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.util.enums.AlertSeverity;
import vdt.mini.management_service.util.enums.RollbackStrategy;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EndpointSettingsService {

    private static final Logger log = LoggerFactory.getLogger(EndpointSettingsService.class);

    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final RedisSettingsSyncService redisSettingsSyncService;

    @Transactional
    public void updateInboundSettings(String endpointId, InboundSettingsPatchRequest request) {
        InboundEndpoint endpoint = inboundEndpointRepository.findByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));

        validateInboundRequest(request);

        // Apply non-null fields to endpoint
        if (request.getRateLimit() != null) {
            endpoint.setRateLimit(request.getRateLimit());
        }
        if (request.getRateLimitWindowSeconds() != null) {
            endpoint.setRateLimitWindowSeconds(request.getRateLimitWindowSeconds());
        }
        if (request.getTimeoutMs() != null) {
            endpoint.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getRequestSizeLimitKb() != null) {
            endpoint.setRequestSizeLimitKb(request.getRequestSizeLimitKb());
        }
        if (request.getResponseSizeLimitKb() != null) {
            endpoint.setResponseSizeLimitKb(request.getResponseSizeLimitKb());
        }
        if (request.getResponseTimeThresholdMs() != null) {
            endpoint.setResponseTimeThresholdMs(request.getResponseTimeThresholdMs());
        }
        if (request.getLogRetentionDays() != null) {
            endpoint.setLogRetentionDays(request.getLogRetentionDays());
        }

        // Apply non-null alert fields
        updateAlertConfig(endpoint, request.getAlertSeverity(),
                request.getAlertThrottleMinutes(), request.getAlertChannels());

        inboundEndpointRepository.save(endpoint);

        registerAfterCommitSync(endpoint.getSecureService().getId());
        log.info("Updated inbound endpoint settings: endpointId={}", endpointId);
    }

    @Transactional
    public void updateOutboundSettings(String endpointId, OutboundSettingsPatchRequest request) {
        OutboundEndpoint endpoint = outboundEndpointRepository.findByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.OUTBOUND_ENDPOINT_NOT_FOUND));

        validateOutboundRequest(request);

        // Apply non-null fields to endpoint
        if (request.getTimeoutMs() != null) {
            endpoint.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getRetryCount() != null) {
            endpoint.setRetryCount(request.getRetryCount());
        }
        if (request.getRetryBackoffMs() != null) {
            endpoint.setRetryBackoffMs(request.getRetryBackoffMs());
        }
        if (request.getResponseTimeThresholdMs() != null) {
            endpoint.setResponseTimeThresholdMs(request.getResponseTimeThresholdMs());
        }
        if (request.getLogRetentionDays() != null) {
            endpoint.setLogRetentionDays(request.getLogRetentionDays());
        }
        if (request.getRollbackStrategy() != null) {
            endpoint.setRollbackStrategy(RollbackStrategy.valueOf(request.getRollbackStrategy()));
        }

        // Apply non-null alert fields
        updateAlertConfig(endpoint, request.getAlertSeverity(),
                request.getAlertThrottleMinutes(), request.getAlertChannels());

        outboundEndpointRepository.save(endpoint);

        registerAfterCommitSync(endpoint.getSecureService().getId());
        log.info("Updated outbound endpoint settings: endpointId={}", endpointId);
    }

    private void updateAlertConfig(InboundEndpoint endpoint, String severity,
                                   Integer throttleMinutes, List<String> channels) {
        AlertConfig alertConfig = endpoint.getAlertConfig();
        if (alertConfig == null) {
            // Create new alert config if endpoint doesn't have one
            AlertConfig newAlertConfig = new AlertConfig();
            newAlertConfig.setId(java.util.UUID.randomUUID().toString());
            newAlertConfig.setName(endpoint.getName() + "-alert");
            newAlertConfig.setChannels(channels != null ? channels : List.of("EMAIL"));
            newAlertConfig.setSeverity(severity != null ? AlertSeverity.valueOf(severity) : AlertSeverity.WARNING);
            newAlertConfig.setThrottleMinutes(throttleMinutes != null ? throttleMinutes : 5);
            endpoint.setAlertConfig(alertConfigRepository.save(newAlertConfig));
        } else {
            boolean modified = false;
            if (severity != null) {
                alertConfig.setSeverity(AlertSeverity.valueOf(severity));
                modified = true;
            }
            if (throttleMinutes != null) {
                alertConfig.setThrottleMinutes(throttleMinutes);
                modified = true;
            }
            if (channels != null) {
                alertConfig.setChannels(channels);
                modified = true;
            }
            if (modified) {
                alertConfigRepository.save(alertConfig);
            }
        }
    }

    private void updateAlertConfig(OutboundEndpoint endpoint, String severity,
                                   Integer throttleMinutes, List<String> channels) {
        AlertConfig alertConfig = endpoint.getAlertConfig();
        if (alertConfig == null) {
            AlertConfig newAlertConfig = new AlertConfig();
            newAlertConfig.setId(java.util.UUID.randomUUID().toString());
            newAlertConfig.setName(endpoint.getName() + "-alert");
            newAlertConfig.setChannels(channels != null ? channels : List.of("LOG"));
            newAlertConfig.setSeverity(severity != null ? AlertSeverity.valueOf(severity) : AlertSeverity.WARNING);
            newAlertConfig.setThrottleMinutes(throttleMinutes != null ? throttleMinutes : 5);
            endpoint.setAlertConfig(alertConfigRepository.save(newAlertConfig));
        } else {
            boolean modified = false;
            if (severity != null) {
                alertConfig.setSeverity(AlertSeverity.valueOf(severity));
                modified = true;
            }
            if (throttleMinutes != null) {
                alertConfig.setThrottleMinutes(throttleMinutes);
                modified = true;
            }
            if (channels != null) {
                alertConfig.setChannels(channels);
                modified = true;
            }
            if (modified) {
                alertConfigRepository.save(alertConfig);
            }
        }
    }

    private void validateInboundRequest(InboundSettingsPatchRequest request) {
        if (request.getRateLimit() != null && request.getRateLimit() < 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "rateLimit must be >= 0");
        }
        if (request.getRateLimitWindowSeconds() != null && request.getRateLimitWindowSeconds() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "rateLimitWindowSeconds must be > 0");
        }
        if (request.getTimeoutMs() != null && request.getTimeoutMs() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "timeoutMs must be > 0");
        }
        if (request.getRequestSizeLimitKb() != null && request.getRequestSizeLimitKb() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "requestSizeLimitKb must be > 0");
        }
        if (request.getResponseSizeLimitKb() != null && request.getResponseSizeLimitKb() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "responseSizeLimitKb must be > 0");
        }
        if (request.getAlertSeverity() != null) {
            try {
                AlertSeverity.valueOf(request.getAlertSeverity());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_INPUT, "alertSeverity must be INFO, WARNING, or CRITICAL");
            }
        }
    }

    private void validateOutboundRequest(OutboundSettingsPatchRequest request) {
        if (request.getTimeoutMs() != null && request.getTimeoutMs() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "timeoutMs must be > 0");
        }
        if (request.getRetryCount() != null && request.getRetryCount() < 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "retryCount must be >= 0");
        }
        if (request.getRetryBackoffMs() != null && request.getRetryBackoffMs() <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "retryBackoffMs must be > 0");
        }
        if (request.getRollbackStrategy() != null) {
            try {
                RollbackStrategy.valueOf(request.getRollbackStrategy());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_INPUT, "rollbackStrategy must be COMPENSATE or IGNORE");
            }
        }
        if (request.getAlertSeverity() != null) {
            try {
                AlertSeverity.valueOf(request.getAlertSeverity());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_INPUT, "alertSeverity must be INFO, WARNING, or CRITICAL");
            }
        }
    }

    private void registerAfterCommitSync(String serviceId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisSettingsSyncService.syncAllEndpointsOfService(serviceId);
                    }
                }
        );
    }
}
