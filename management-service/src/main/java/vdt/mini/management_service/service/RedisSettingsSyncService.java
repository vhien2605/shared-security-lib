package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.sync.AccessRuleDTO;
import vdt.mini.management_service.dto.sync.AuthConfigDTO;
import vdt.mini.management_service.dto.sync.InboundSettingsSyncDTO;
import vdt.mini.management_service.dto.sync.OutboundSettingsSyncDTO;
import vdt.mini.management_service.dto.sync.SettingsChangeMessage;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.util.enums.EndpointStatus;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.util.Collections;
import java.util.List;

@Service
public class RedisSettingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(RedisSettingsSyncService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;

    public RedisSettingsSyncService(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    InboundEndpointRepository inboundEndpointRepository,
                                    OutboundEndpointRepository outboundEndpointRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.outboundEndpointRepository = outboundEndpointRepository;
    }

    public void syncInboundToRedis(InboundEndpoint endpoint) {
        try {
            InboundSettingsSyncDTO dto = buildInboundSyncDTO(endpoint);
            String key = "security:config:inbound:" + endpoint.getId();
            if (Boolean.TRUE.equals(endpoint.getEnabled())) {
                String json = objectMapper.writeValueAsString(dto);
                redisTemplate.opsForValue().set(key, json);
            } else {
                redisTemplate.delete(key);
            }

            SettingsChangeMessage message = new SettingsChangeMessage(
                    "INBOUND",
                    endpoint.getId(),
                    endpoint.getSecureService().getId(),
                    dto
            );
            String channel = "security:settings:" + endpoint.getSecureService().getId();
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.debug("Synced inbound settings to Redis: endpointId={}, serviceId={}",
                    endpoint.getId(), endpoint.getSecureService().getId());
        } catch (Exception e) {
            log.error("Failed to sync inbound settings to Redis for endpointId={}", endpoint.getId(), e);
        }
    }

    public void syncOutboundToRedis(OutboundEndpoint endpoint) {
        try {
            OutboundSettingsSyncDTO dto = buildOutboundSyncDTO(endpoint);
            String key = "security:config:outbound:" + endpoint.getId();
            if (Boolean.TRUE.equals(endpoint.getEnabled())) {
                String json = objectMapper.writeValueAsString(dto);
                redisTemplate.opsForValue().set(key, json);
            } else {
                redisTemplate.delete(key);
            }

            SettingsChangeMessage message = new SettingsChangeMessage(
                    "OUTBOUND",
                    endpoint.getId(),
                    endpoint.getSecureService().getId(),
                    dto
            );
            String channel = "security:settings:" + endpoint.getSecureService().getId();
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.debug("Synced outbound settings to Redis: endpointId={}, serviceId={}",
                    endpoint.getId(), endpoint.getSecureService().getId());
        } catch (Exception e) {
            log.error("Failed to sync outbound settings to Redis for endpointId={}", endpoint.getId(), e);
        }
    }

    public void syncAllEndpointsOfService(String serviceId) {
        try {
            List<InboundEndpoint> inbounds = inboundEndpointRepository.findAllBySecureServiceIdWithAll(serviceId);
            for (InboundEndpoint ep : inbounds) {
                syncInboundToRedis(ep);
            }
            List<OutboundEndpoint> outbounds = outboundEndpointRepository.findAllBySecureServiceIdWithAlert(serviceId);
            for (OutboundEndpoint ep : outbounds) {
                syncOutboundToRedis(ep);
            }
            log.info("Synced all endpoints of service {} to Redis ({} inbound, {} outbound)",
                    serviceId, inbounds.size(), outbounds.size());
        } catch (Exception e) {
            log.error("Failed to sync all endpoints of service {} to Redis", serviceId, e);
        }
    }

    private InboundSettingsSyncDTO buildInboundSyncDTO(InboundEndpoint ep) {
        InboundSettingsSyncDTO dto = new InboundSettingsSyncDTO();
        dto.setEndpointId(ep.getId());
        dto.setName(ep.getName());
        dto.setPath(ep.getPath());
        dto.setTopic(ep.getTopic());
        dto.setMethod(ep.getMethod() != null ? ep.getMethod().name() : null);
        dto.setProtocol(ep.getProtocol() != null ? ep.getProtocol().name() : null);
        dto.setEnabled(ep.getEnabled());
        dto.setEndpointStatus(endpointStatus(ep.getStatus()).name());
        dto.setServiceStatus(serviceStatus(ep.getSecureService()).name());
        dto.setAvailable(isAvailable(ep.getSecureService(), ep.getEnabled(), ep.getStatus()));
        dto.setRateLimit(ep.getRateLimit());
        dto.setRateLimitWindowSeconds(ep.getRateLimitWindowSeconds());
        dto.setTimeoutMs(ep.getTimeoutMs());
        dto.setRequestSizeLimitKb(ep.getRequestSizeLimitKb());
        dto.setResponseSizeLimitKb(ep.getResponseSizeLimitKb());
        dto.setResponseTimeThresholdMs(ep.getResponseTimeThresholdMs());
        dto.setLogRetentionDays(ep.getLogRetentionDays());
        if (ep.getAlertConfig() != null) {
            dto.setAlertSeverity(ep.getAlertConfig().getSeverity() != null ? ep.getAlertConfig().getSeverity().name() : null);
            dto.setAlertThrottleMinutes(ep.getAlertConfig().getThrottleMinutes());
            dto.setAlertChannels(ep.getAlertConfig().getChannels());
        }
        // Auth configs and access rules are loaded from entity relationships
        if (ep.getAuthConfigs() != null) {
            dto.setAuthConfigs(ep.getAuthConfigs().stream()
                    .map(ac -> new AuthConfigDTO(
                            ac.getType() != null ? ac.getType().name() : null,
                            ac.getSecretRef(),
                            ac.getAlgorithm(),
                            ac.getExpiresAt() != null ? ac.getExpiresAt().toString() : null,
                            null))
                    .toList());
        } else {
            dto.setAuthConfigs(Collections.emptyList());
        }
        if (ep.getAccessRules() != null) {
            dto.setAccessRules(ep.getAccessRules().stream()
                    .map(ar -> new AccessRuleDTO(ar.getType() != null ? ar.getType().name() : null,
                            ar.getValueType() != null ? ar.getValueType().name() : null,
                            ar.getValue(),
                            ar.getTemporary(),
                            ar.getExpiresAt() != null ? ar.getExpiresAt().toString() : null))
                    .toList());
        } else {
            dto.setAccessRules(Collections.emptyList());
        }
        return dto;
    }

    private OutboundSettingsSyncDTO buildOutboundSyncDTO(OutboundEndpoint ep) {
        OutboundSettingsSyncDTO dto = new OutboundSettingsSyncDTO();
        dto.setEndpointId(ep.getId());
        dto.setName(ep.getName());
        dto.setTargetUrl(ep.getTargetUrl());
        dto.setTopic(ep.getTopic());
        dto.setMethod(ep.getMethod() != null ? ep.getMethod().name() : null);
        dto.setProtocol(ep.getProtocol() != null ? ep.getProtocol().name() : null);
        dto.setEnabled(ep.getEnabled());
        dto.setEndpointStatus(endpointStatus(ep.getStatus()).name());
        dto.setServiceStatus(serviceStatus(ep.getSecureService()).name());
        dto.setAvailable(isAvailable(ep.getSecureService(), ep.getEnabled(), ep.getStatus()));
        dto.setTimeoutMs(ep.getTimeoutMs());
        dto.setRetryCount(ep.getRetryCount());
        dto.setRetryBackoffMs(ep.getRetryBackoffMs());
        dto.setResponseTimeThresholdMs(ep.getResponseTimeThresholdMs());
        dto.setLogRetentionDays(ep.getLogRetentionDays());
        dto.setRollbackStrategy(ep.getRollbackStrategy() != null ? ep.getRollbackStrategy().name() : null);
        if (ep.getAlertConfig() != null) {
            dto.setAlertSeverity(ep.getAlertConfig().getSeverity() != null ? ep.getAlertConfig().getSeverity().name() : null);
            dto.setAlertThrottleMinutes(ep.getAlertConfig().getThrottleMinutes());
            dto.setAlertChannels(ep.getAlertConfig().getChannels());
        }
        return dto;
    }

    private boolean isAvailable(vdt.mini.management_service.entity.SecureService service,
                                Boolean enabled,
                                EndpointStatus endpointStatus) {
        return serviceStatus(service) == ServiceStatus.ACTIVE
                && Boolean.TRUE.equals(enabled)
                && endpointStatus(endpointStatus) == EndpointStatus.ACTIVE;
    }

    private ServiceStatus serviceStatus(vdt.mini.management_service.entity.SecureService service) {
        return service != null && service.getStatus() != null ? service.getStatus() : ServiceStatus.INACTIVE;
    }

    private EndpointStatus endpointStatus(EndpointStatus status) {
        return status != null ? status : EndpointStatus.ACTIVE;
    }
}
