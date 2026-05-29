package vdt.mini.management_service.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.event.InboundEndpointDTO;
import vdt.mini.management_service.dto.event.OutboundEndpointDTO;
import vdt.mini.management_service.dto.event.ServiceRegistrationEvent;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.repository.AlertConfigRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.AlertSeverity;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.RollbackStrategy;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.util.ArrayList;
import java.util.List;


@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final ServiceRepository serviceRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final RedisSettingsSyncService redisSettingsSyncService;

    @Value("${app.security.inbound.default.rate-limit}")
    private int defaultRateLimit;

    @Value("${app.security.inbound.default.rate-limit-window-seconds}")
    private int defaultRateLimitWindowSeconds;

    @Value("${app.security.inbound.default.timeout-ms}")
    private int inboundDefaultTimeoutMs;

    @Value("${app.security.inbound.default.request-size-limit-kb}")
    private int inboundDefaultRequestSizeLimitKb;

    @Value("${app.security.inbound.default.response-size-limit-kb}")
    private int inboundDefaultResponseSizeLimitKb;

    @Value("${app.security.inbound.default.response-time-threshold-ms}")
    private int inboundDefaultResponseTimeThresholdMs;

    @Value("${app.security.inbound.default.log-retention-days}")
    private int inboundDefaultLogRetentionDays;

    @Value("${app.security.outbound.default.timeout-ms}")
    private int outboundDefaultTimeoutMs;

    @Value("${app.security.outbound.default.response-time-threshold-ms}")
    private int outboundDefaultResponseTimeThresholdMs;

    @Value("${app.security.outbound.default.retry-count}")
    private int defaultRetryCount;

    @Value("${app.security.outbound.default.retry-backoff-ms}")
    private int defaultRetryBackoffMs;

    @Value("${app.security.outbound.default.log-retention-days}")
    private int outboundDefaultLogRetentionDays;

    @Value("${app.security.alert.default.severity:WARNING}")
    private String defaultAlertSeverity;

    @Value("${app.security.alert.default.throttle-minutes:5}")
    private int defaultAlertThrottleMinutes;

    @Value("${app.security.alert.default.channels:LOG}")
    private String defaultAlertChannels;

    public RegistrationService(ServiceRepository serviceRepository,
                               InboundEndpointRepository inboundEndpointRepository,
                               OutboundEndpointRepository outboundEndpointRepository,
                               AlertConfigRepository alertConfigRepository,
                               RedisSettingsSyncService redisSettingsSyncService) {
        this.serviceRepository = serviceRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.outboundEndpointRepository = outboundEndpointRepository;
        this.alertConfigRepository = alertConfigRepository;
        this.redisSettingsSyncService = redisSettingsSyncService;
    }

    @Transactional
    public void processRegistration(ServiceRegistrationEvent event) {
        log.info("TX={}", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("THREAD={}, SERVICE_ID={}",
                Thread.currentThread().getName(),
                event.getServiceId());

        // =========================
        // 1. UPSERT SERVICE - TRÁNH MERGE CHO ENTITY ĐÃ MANAGED
        // =========================
        SecureService service = serviceRepository.findById(event.getServiceId())
                .orElse(null);

        if (service != null) {
            service.setName(event.getServiceName());
            service.setBaseUrl(event.getBaseUrl());
            service.setDescription(event.getDescription());
        } else {
            service = new SecureService();
            service.setId(event.getServiceId());
            service.setStatus(ServiceStatus.ACTIVE);
            service.setName(event.getServiceName());
            service.setBaseUrl(event.getBaseUrl());
            service.setDescription(event.getDescription());
            serviceRepository.save(service);
        }


        // =========================
        // 2. INBOUNDS (BATCH SAFE)
        // =========================
        List<InboundEndpoint> inbounds = new ArrayList<>();

        for (InboundEndpointDTO dto : event.getInbounds()) {

            InboundEndpoint ep = inboundEndpointRepository.findByIdWithAlert(dto.getEndpointId())
                    .orElseGet(() -> {
                        InboundEndpoint created = new InboundEndpoint();
                        created.setId(dto.getEndpointId());
                        return created;
                    });

            ep.setSecureService(service);
            ep.setName(dto.getName());
            ep.setPath(dto.getPath());
            ep.setMethod(dto.getMethod() != null
                    ? EndpointMethod.valueOf(dto.getMethod())
                    : null);
            ep.setProtocol(dto.getProtocol() != null
                    ? EndpointProtocol.valueOf(dto.getProtocol())
                    : null);

            if (ep.getRateLimit() == null) {
                ep.setRateLimit(defaultRateLimit);
            }
            if (ep.getRateLimitWindowSeconds() == null) {
                ep.setRateLimitWindowSeconds(defaultRateLimitWindowSeconds);
            }
            if (ep.getTimeoutMs() == null) {
                ep.setTimeoutMs(inboundDefaultTimeoutMs);
            }
            if (ep.getLogRetentionDays() == null) {
                ep.setLogRetentionDays(inboundDefaultLogRetentionDays);
            }
            if (ep.getRequestSizeLimitKb() == null) {
                ep.setRequestSizeLimitKb(inboundDefaultRequestSizeLimitKb);
            }
            if (ep.getResponseSizeLimitKb() == null) {
                ep.setResponseSizeLimitKb(inboundDefaultResponseSizeLimitKb);
            }
            if (ep.getResponseTimeThresholdMs() == null) {
                ep.setResponseTimeThresholdMs(inboundDefaultResponseTimeThresholdMs);
            }

            // Tạo AlertConfig mới nếu endpoint chưa có
            if (ep.getAlertConfig() == null) {
                AlertConfig newAlertConfig = new AlertConfig();
                newAlertConfig.setId(java.util.UUID.randomUUID().toString());
                newAlertConfig.setName(dto.getName() + "-alert");
                newAlertConfig.setSeverity(AlertSeverity.valueOf(defaultAlertSeverity));
                newAlertConfig.setThrottleMinutes(defaultAlertThrottleMinutes);
                newAlertConfig.setChannels(java.util.List.of(defaultAlertChannels.split(",")));
                ep.setAlertConfig(alertConfigRepository.save(newAlertConfig));
            }

            inbounds.add(ep);
        }
        inboundEndpointRepository.saveAll(inbounds);
        // =========================
        // 3. OUTBOUNDS (BATCH SAFE)
        // =========================
        List<OutboundEndpoint> outbounds = new ArrayList<>();

        for (OutboundEndpointDTO dto : event.getOutbounds()) {

            OutboundEndpoint ep = outboundEndpointRepository.findByIdWithAlert(dto.getEndpointId())
                    .orElseGet(() -> {
                        OutboundEndpoint created = new OutboundEndpoint();
                        created.setId(dto.getEndpointId());
                        return created;
                    });

            ep.setSecureService(service);
            ep.setName(dto.getName());
            ep.setTargetUrl(dto.getTargetUrl());
            ep.setMethod(dto.getMethod() != null
                    ? EndpointMethod.valueOf(dto.getMethod())
                    : null);
            ep.setProtocol(dto.getProtocol() != null
                    ? EndpointProtocol.valueOf(dto.getProtocol())
                    : null);

            if (ep.getTimeoutMs() == null) {
                ep.setTimeoutMs(outboundDefaultTimeoutMs);
            }
            if (ep.getRetryCount() == null) {
                ep.setRetryCount(defaultRetryCount);
            }
            if (ep.getRetryBackoffMs() == null) {
                ep.setRetryBackoffMs(defaultRetryBackoffMs);
            }
            if (ep.getLogRetentionDays() == null) {
                ep.setLogRetentionDays(outboundDefaultLogRetentionDays);
            }
            if (ep.getResponseTimeThresholdMs() == null) {
                ep.setResponseTimeThresholdMs(outboundDefaultResponseTimeThresholdMs);
            }
            if (ep.getRollbackStrategy() == null) {
                ep.setRollbackStrategy(RollbackStrategy.IGNORE);
            }

            // Tạo AlertConfig mới nếu endpoint chưa có
            if (ep.getAlertConfig() == null) {
                AlertConfig newAlertConfig = new AlertConfig();
                newAlertConfig.setId(java.util.UUID.randomUUID().toString());
                newAlertConfig.setName(dto.getName() + "-alert");
                newAlertConfig.setSeverity(AlertSeverity.valueOf(defaultAlertSeverity));
                newAlertConfig.setThrottleMinutes(defaultAlertThrottleMinutes);
                newAlertConfig.setChannels(java.util.List.of(defaultAlertChannels.split(",")));
                ep.setAlertConfig(alertConfigRepository.save(newAlertConfig));
            }

            outbounds.add(ep);
        }
        outboundEndpointRepository.saveAll(outbounds);

        log.info("Processed registration: service={}, inbounds={}, outbounds={}",
                event.getServiceName(),
                inbounds.size(),
                outbounds.size());

        // Đăng ký afterCommit hook để sync Redis sau khi transaction thành công
        String serviceId = event.getServiceId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisSettingsSyncService.syncAllEndpointsOfService(serviceId);
                    }
                }
        );
        log.info("Registered afterCommit hook for Redis sync: serviceId={}", serviceId);
    }
}
