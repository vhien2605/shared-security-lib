package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.sync.AccessRuleDTO;
import vdt.mini.management_service.dto.sync.AccessPermissionDTO;
import vdt.mini.management_service.dto.sync.AuthConfigDTO;
import vdt.mini.management_service.dto.sync.AuthConfigRuntimeDTO;
import vdt.mini.management_service.dto.sync.ClientRuntimeDTO;
import vdt.mini.management_service.dto.sync.InboundSettingsSyncDTO;
import vdt.mini.management_service.dto.sync.OutboundSettingsSyncDTO;
import vdt.mini.management_service.dto.sync.PermissionRuntimeDTO;
import vdt.mini.management_service.dto.sync.RuntimeChangePayloadDTO;
import vdt.mini.management_service.dto.sync.RuntimeManifestDTO;
import vdt.mini.management_service.dto.sync.RuntimeTombstoneDTO;
import vdt.mini.management_service.dto.sync.SecurityRuntimeChangeMessage;
import vdt.mini.management_service.dto.sync.ServiceAuthConfigsSnapshotDTO;
import vdt.mini.management_service.dto.sync.ServiceClientsSnapshotDTO;
import vdt.mini.management_service.dto.sync.ServicePermissionsSnapshotDTO;
import vdt.mini.management_service.dto.sync.SettingsChangeMessage;
import vdt.mini.management_service.entity.AccessPermission;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.repository.AccessPermissionRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.util.enums.AuthType;
import vdt.mini.management_service.util.enums.EndpointStatus;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RedisSettingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(RedisSettingsSyncService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthConfigRepository authConfigRepository;
    private final AccessPermissionRepository accessPermissionRepository;
    private final ClientRepository clientRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final SecretCipherService secretCipherService;

    public RedisSettingsSyncService(StringRedisTemplate redisTemplate,
                                        ObjectMapper objectMapper,
                                        AuthConfigRepository authConfigRepository,
                                        AccessPermissionRepository accessPermissionRepository,
                                        InboundEndpointRepository inboundEndpointRepository,
                                        OutboundEndpointRepository outboundEndpointRepository,
                                        SecretCipherService secretCipherService) {
        this(redisTemplate, objectMapper, authConfigRepository, accessPermissionRepository, null,
                inboundEndpointRepository, outboundEndpointRepository, secretCipherService);
    }

    @Autowired
    public RedisSettingsSyncService(StringRedisTemplate redisTemplate,
                                        ObjectMapper objectMapper,
                                        AuthConfigRepository authConfigRepository,
                                        AccessPermissionRepository accessPermissionRepository,
                                        ClientRepository clientRepository,
                                        InboundEndpointRepository inboundEndpointRepository,
                                       OutboundEndpointRepository outboundEndpointRepository,
                                       SecretCipherService secretCipherService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authConfigRepository = authConfigRepository;
        this.accessPermissionRepository = accessPermissionRepository;
        this.clientRepository = clientRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.outboundEndpointRepository = outboundEndpointRepository;
        this.secretCipherService = secretCipherService;
    }

    public void syncInboundToRedis(InboundEndpoint endpoint) {
        try {
            InboundSettingsSyncDTO dto = buildInboundSyncDTO(endpoint);
            String key = RedisSecurityRuntimeKeys.inboundSettings(endpoint.getId());
            if (Boolean.TRUE.equals(endpoint.getEnabled())) {
                String json = objectMapper.writeValueAsString(dto);
                redisTemplate.opsForValue().set(key, json);
            } else {
                redisTemplate.delete(key);
            }

            String operation = Boolean.TRUE.equals(endpoint.getEnabled()) ? "UPSERT" : "REMOVE";
            SettingsChangeMessage message = settingsMessage("INBOUND", endpoint.getId(), endpoint.getSecureService().getId(),
                    Boolean.TRUE.equals(endpoint.getEnabled()) ? dto : null, operation, List.of("inboundSettings"));
            String serviceId = endpoint.getSecureService().getId();
            String channel = RedisSecurityRuntimeKeys.legacySettingsChannel(serviceId);
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.info("Redis endpoint settings v2 publish completed type=INBOUND operation={} endpointId={} serviceId={} channel={}",
                    operation, endpoint.getId(), serviceId, channel);
        } catch (Exception e) {
            log.error("Failed to sync inbound settings to Redis for endpointId={}", endpoint.getId(), e);
        }
    }

    public void syncOutboundToRedis(OutboundEndpoint endpoint) {
        try {
            OutboundSettingsSyncDTO dto = buildOutboundSyncDTO(endpoint);
            String key = RedisSecurityRuntimeKeys.outboundSettings(endpoint.getId());
            if (Boolean.TRUE.equals(endpoint.getEnabled())) {
                String json = objectMapper.writeValueAsString(dto);
                redisTemplate.opsForValue().set(key, json);
            } else {
                redisTemplate.delete(key);
            }

            String operation = Boolean.TRUE.equals(endpoint.getEnabled()) ? "UPSERT" : "REMOVE";
            SettingsChangeMessage message = settingsMessage("OUTBOUND", endpoint.getId(), endpoint.getSecureService().getId(),
                    Boolean.TRUE.equals(endpoint.getEnabled()) ? dto : null, operation, List.of("outboundSettings"));
            String serviceId = endpoint.getSecureService().getId();
            String channel = RedisSecurityRuntimeKeys.legacySettingsChannel(serviceId);
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.info("Redis endpoint settings v2 publish completed type=OUTBOUND operation={} endpointId={} serviceId={} channel={}",
                    operation, endpoint.getId(), serviceId, channel);
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
            log.info("Synced all endpoint settings of service {} to Redis without runtime snapshot ({} inbound, {} outbound)",
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
        String serviceId = ep.getSecureService() != null ? ep.getSecureService().getId() : null;
        List<AuthConfig> authConfigs = serviceId != null
                ? authConfigRepository.findEnabledByServiceScope(serviceId)
                : Collections.emptyList();
        dto.setAuthConfigs(authConfigs.stream()
                .map(this::toSyncAuthConfig)
                .toList());
        if (ep.getAccessRules() != null) {
            dto.setAccessRules(ep.getAccessRules().stream()
                    .filter(ar -> Boolean.TRUE.equals(ar.getEnable()))
                    .filter(ar -> !isExpired(ar.getTemporary(), ar.getExpiresAt()))
                    .map(ar -> new AccessRuleDTO(ar.getType() != null ? ar.getType().name() : null,
                            ar.getValueType() != null ? ar.getValueType().name() : null,
                            ar.getValue(),
                            ar.getTemporary(),
                            ar.getExpiresAt() != null ? ar.getExpiresAt().toString() : null))
                    .toList());
        } else {
            dto.setAccessRules(Collections.emptyList());
        }
        dto.setPermissions(accessPermissionRepository.findEnabledByInboundEndpointId(ep.getId()).stream()
                .map(this::toPermissionDTO)
                .toList());
        return dto;
    }

    private SettingsChangeMessage settingsMessage(String type, String endpointId, String serviceId, Object config,
                                                  String operation, List<String> changedFields) {
        return new SettingsChangeMessage(type, endpointId, serviceId, config, operation,
                System.currentTimeMillis(), LocalDateTime.now().toString(), changedFields);
    }

    public void syncRuntimeSnapshotOfService(String serviceId) {
        syncRuntimeSnapshotOfService(serviceId,
                inboundEndpointRepository.findAllBySecureServiceId(serviceId).size(),
                outboundEndpointRepository.findAllBySecureServiceId(serviceId).size());
    }

    private void syncRuntimeSnapshotOfService(String serviceId, int inboundCount, int outboundCount) {
        if (serviceId == null || serviceId.isBlank()) {
            return;
        }
        if (clientRepository == null) {
            log.warn("Redis runtime snapshot skipped because ClientRepository is unavailable serviceId={}", serviceId);
            return;
        }
        long version = System.currentTimeMillis();
        try {
            log.info("Redis runtime snapshot write started serviceId={} version={}", serviceId, version);
            List<ClientRuntimeDTO> clients = clientRepository.findRuntimeClientsByServiceId(serviceId).stream()
                    .map(this::toClientRuntimeDTO)
                    .toList();
            List<AuthConfigRuntimeDTO> authConfigs = authConfigRepository.findRuntimeByServiceId(serviceId).stream()
                    .map(this::toAuthConfigRuntimeDTO)
                    .toList();
            List<PermissionRuntimeDTO> permissions = accessPermissionRepository.findRuntimeByServiceId(serviceId).stream()
                    .map(this::toPermissionRuntimeDTO)
                    .toList();

            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.inboundEndpointIds(serviceId),
                    objectMapper.writeValueAsString(inboundEndpointRepository.findAllBySecureServiceId(serviceId).stream().map(InboundEndpoint::getId).toList()));
            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.outboundEndpointIds(serviceId),
                    objectMapper.writeValueAsString(outboundEndpointRepository.findAllBySecureServiceId(serviceId).stream().map(OutboundEndpoint::getId).toList()));
            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.clients(serviceId),
                    objectMapper.writeValueAsString(new ServiceClientsSnapshotDTO(serviceId, version, clients)));
            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.authConfigs(serviceId),
                    objectMapper.writeValueAsString(new ServiceAuthConfigsSnapshotDTO(serviceId, version, authConfigs)));
            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.permissions(serviceId),
                    objectMapper.writeValueAsString(new ServicePermissionsSnapshotDTO(serviceId, version, permissions)));
            RuntimeManifestDTO manifest = new RuntimeManifestDTO(serviceId, version, LocalDateTime.now().toString(),
                    inboundCount, outboundCount, clients.size(), authConfigs.size(), permissions.size(), runtimeKeys(serviceId));
            redisTemplate.opsForValue().set(RedisSecurityRuntimeKeys.manifest(serviceId), objectMapper.writeValueAsString(manifest));
            log.info("Redis runtime manifest write completed serviceId={} version={} inboundCount={} outboundCount={} clientCount={} authConfigCount={} permissionCount={}",
                    serviceId, version, inboundCount, outboundCount, clients.size(), authConfigs.size(), permissions.size());
            publishRuntimeChange(new SecurityRuntimeChangeMessage(UUID.randomUUID().toString(), "SERVICE_SNAPSHOT_REFRESHED",
                    serviceId, null, null, null, null, List.of("runtimeSnapshot"), version, LocalDateTime.now().toString(), null));
            log.info("Redis runtime snapshot write completed serviceId={} version={}", serviceId, version);
        } catch (Exception ex) {
            log.error("Failed to write Redis runtime snapshot serviceId={} version={}", serviceId, version, ex);
        }
    }

    public void publishRuntimeChange(SecurityRuntimeChangeMessage message) {
        if (message == null || message.getServiceId() == null || message.getServiceId().isBlank()) {
            return;
        }
        String channel = RedisSecurityRuntimeKeys.eventsChannel(message.getServiceId());
        try {
            log.info("Redis runtime event publish started eventType={} channel={} serviceId={} version={} endpointId={} clientId={} authConfigId={} permissionId={}",
                    message.getEventType(), channel, message.getServiceId(), message.getVersion(), message.getEndpointId(), message.getClientId(), message.getAuthConfigId(), message.getPermissionId());
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(message));
            log.info("Redis runtime event publish completed eventType={} channel={} serviceId={} version={}",
                    message.getEventType(), channel, message.getServiceId(), message.getVersion());
        } catch (Exception ex) {
            log.error("Failed to publish Redis runtime event eventType={} serviceId={} version={}",
                    message.getEventType(), message.getServiceId(), message.getVersion(), ex);
        }
    }

    public void publishClientRuntimeChange(String serviceId, String clientId, String eventType, List<String> changedFields) {
        if (clientRepository == null || serviceId == null || serviceId.isBlank() || clientId == null || clientId.isBlank()) {
            return;
        }
        clientRepository.findById(clientId).ifPresentOrElse(client -> {
            RuntimeChangePayloadDTO payload = new RuntimeChangePayloadDTO(toClientRuntimeDTO(client),
                    authConfigRepository.findRuntimeByServiceId(serviceId).stream()
                            .filter(auth -> auth.getClient() != null && clientId.equals(auth.getClient().getId()))
                            .map(this::toAuthConfigRuntimeDTO)
                            .toList(),
                    accessPermissionRepository.findRuntimeByServiceId(serviceId).stream()
                            .filter(permission -> permission.getClient() != null && clientId.equals(permission.getClient().getId()))
                            .map(this::toPermissionRuntimeDTO)
                            .toList(),
                    List.of());
            publishRuntimeChange(new SecurityRuntimeChangeMessage(UUID.randomUUID().toString(), eventType, serviceId,
                    null, clientId, null, null, changedFields, eventVersion(payload.getClient().getVersion()),
                    LocalDateTime.now().toString(), payload));
        }, () -> log.warn("Runtime client event skipped because client not found serviceId={} clientId={}", serviceId, clientId));
    }

    public void publishAuthConfigRuntimeChange(String authConfigId, String eventType, String tombstoneReason) {
        authConfigRepository.findById(authConfigId).ifPresentOrElse(authConfig -> {
            AuthConfigRuntimeDTO auth = toAuthConfigRuntimeDTO(authConfig);
            RuntimeChangePayloadDTO payload = new RuntimeChangePayloadDTO(null,
                    "AUTH_CONFIG_CHANGED".equals(eventType) ? List.of(auth) : List.of(), List.of(),
                    "AUTH_CONFIG_CHANGED".equals(eventType) ? List.of() : List.of(tombstone("AUTH_CONFIG", auth.getServiceId(), null,
                            auth.getClientId(), authConfigId, null, tombstoneReason)));
            publishRuntimeChange(new SecurityRuntimeChangeMessage(UUID.randomUUID().toString(), eventType, auth.getServiceId(),
                    null, auth.getClientId(), authConfigId, null, List.of("authConfig"), eventVersion(auth.getVersion()),
                    LocalDateTime.now().toString(), payload));
        }, () -> log.warn("Runtime auth event skipped because authConfig not found authConfigId={} eventType={}", authConfigId, eventType));
    }

    public void publishAuthConfigDeleted(String serviceId, String clientId, String authConfigId) {
        RuntimeChangePayloadDTO payload = new RuntimeChangePayloadDTO(null, List.of(), List.of(),
                List.of(tombstone("AUTH_CONFIG", serviceId, null, clientId, authConfigId, null, "DELETED")));
        publishRuntimeChange(new SecurityRuntimeChangeMessage(UUID.randomUUID().toString(), "AUTH_CONFIG_DELETED", serviceId,
                null, clientId, authConfigId, null, List.of("authConfig"), System.currentTimeMillis(), LocalDateTime.now().toString(), payload));
    }

    public void publishPermissionRuntimeChange(AccessPermission permission, String eventType, String tombstoneReason) {
        PermissionRuntimeDTO dto = toPermissionRuntimeDTO(permission);
        RuntimeChangePayloadDTO payload = new RuntimeChangePayloadDTO(null, List.of(),
                "PERMISSION_CHANGED".equals(eventType) ? List.of(dto) : List.of(),
                "PERMISSION_CHANGED".equals(eventType) ? List.of() : List.of(tombstone("PERMISSION", dto.getServiceId(), dto.getInboundEndpointId(),
                        dto.getClientId(), null, dto.getPermissionId(), tombstoneReason)));
        publishRuntimeChange(new SecurityRuntimeChangeMessage(UUID.randomUUID().toString(), eventType, dto.getServiceId(),
                dto.getInboundEndpointId(), dto.getClientId(), null, dto.getPermissionId(), List.of("permissions"), eventVersion(dto.getVersion()),
                LocalDateTime.now().toString(), payload));
    }

    private RuntimeTombstoneDTO tombstone(String resourceType, String serviceId, String endpointId, String clientId,
                                          String authConfigId, String permissionId, String reason) {
        return new RuntimeTombstoneDTO(resourceType, serviceId, endpointId, clientId, authConfigId, permissionId, reason);
    }

    private long eventVersion(Long dtoVersion) {
        return dtoVersion != null ? dtoVersion : System.currentTimeMillis();
    }

    private Map<String, String> runtimeKeys(String serviceId) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("clients", RedisSecurityRuntimeKeys.clients(serviceId));
        keys.put("authConfigs", RedisSecurityRuntimeKeys.authConfigs(serviceId));
        keys.put("permissions", RedisSecurityRuntimeKeys.permissions(serviceId));
        keys.put("inboundEndpoints", RedisSecurityRuntimeKeys.inboundEndpointIds(serviceId));
        keys.put("outboundEndpoints", RedisSecurityRuntimeKeys.outboundEndpointIds(serviceId));
        return keys;
    }

    private ClientRuntimeDTO toClientRuntimeDTO(Client client) {
        boolean active = client.getStatus() == vdt.mini.management_service.util.enums.ClientStatus.ACTIVE;
        return new ClientRuntimeDTO(client.getId(), client.getClientKey(), client.getName(),
                client.getStatus() != null ? client.getStatus().name() : null, active, active,
                client.getRevokedAt() != null ? client.getRevokedAt().toString() : null,
                client.getUpdatedAt() != null ? client.getUpdatedAt().toString() : null,
                client.getUpdatedAt() != null ? toVersion(client.getUpdatedAt()) : null);
    }

    private AuthConfigRuntimeDTO toAuthConfigRuntimeDTO(AuthConfig authConfig) {
        Client client = authConfig.getClient();
        return new AuthConfigRuntimeDTO(authConfig.getId(),
                authConfig.getService() != null ? authConfig.getService().getId() : null,
                client != null ? client.getId() : null,
                client != null ? client.getClientKey() : null,
                authConfig.getType() != null ? authConfig.getType().name() : null,
                authConfig.getSecretRef(), authConfig.getCredentialHash(), authConfig.getAlgorithm(), null,
                resolveRuntimeSecretKey(authConfig),
                authConfig.getExpiresAt() != null ? authConfig.getExpiresAt().toString() : null,
                Boolean.TRUE.equals(authConfig.getEnabled()) && client != null && client.getStatus() == vdt.mini.management_service.util.enums.ClientStatus.ACTIVE,
                client != null && client.getStatus() != null ? client.getStatus().name() : null,
                authConfig.getUpdatedAt() != null ? toVersion(authConfig.getUpdatedAt()) : null);
    }

    private PermissionRuntimeDTO toPermissionRuntimeDTO(AccessPermission permission) {
        Client client = permission.getClient();
        InboundEndpoint endpoint = permission.getInboundEndpoint();
        String serviceId = endpoint != null && endpoint.getSecureService() != null ? endpoint.getSecureService().getId() : null;
        boolean enabled = Boolean.TRUE.equals(permission.getEnable()) && client != null && client.getStatus() == vdt.mini.management_service.util.enums.ClientStatus.ACTIVE;
        return new PermissionRuntimeDTO(permission.getId(), serviceId, endpoint != null ? endpoint.getId() : null,
                client != null ? client.getId() : null, client != null ? client.getClientKey() : null,
                enabled, client != null && client.getStatus() != null ? client.getStatus().name() : null,
                permission.getUpdatedAt() != null ? toVersion(permission.getUpdatedAt()) : null);
    }

    private long toVersion(LocalDateTime dateTime) {
        return java.time.ZoneOffset.UTC != null ? dateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : System.currentTimeMillis();
    }

    private String resolveRuntimeSecretKey(AuthConfig authConfig) {
        if (authConfig.getType() != AuthType.HMAC_SIGNATURE) {
            return null;
        }
        return resolveClientKey(authConfig);
    }

    private AuthConfigDTO toSyncAuthConfig(AuthConfig authConfig) {
        return new AuthConfigDTO(
                authConfig.getType() != null ? authConfig.getType().name() : null,
                authConfig.getSecretRef(),
                authConfig.getAlgorithm(),
                authConfig.getExpiresAt() != null ? authConfig.getExpiresAt().toString() : null,
                resolveClientKey(authConfig));
    }

    private String resolveClientKey(AuthConfig authConfig) {
        if (authConfig.getType() != AuthType.HMAC_SIGNATURE) {
            return null;
        }
        if (authConfig.getSecretCiphertext() == null || authConfig.getSecretCiphertext().isBlank()) {
            log.warn("HMAC auth config missing secret ciphertext: authConfigId={}, secretRef={}",
                    authConfig.getId(), authConfig.getSecretRef());
            return null;
        }
        try {
            return secretCipherService.decrypt(authConfig.getSecretCiphertext());
        } catch (RuntimeException ex) {
            log.warn("HMAC auth config secret ciphertext could not be decrypted: authConfigId={}, secretRef={}",
                    authConfig.getId(), authConfig.getSecretRef(), ex);
            return null;
        }
    }

    private AccessPermissionDTO toPermissionDTO(AccessPermission permission) {
        return new AccessPermissionDTO(permission.getId(),
                permission.getClient().getId(),
                permission.getClient().getClientKey(),
                permission.getInboundEndpoint().getId());
    }

    private boolean isExpired(Boolean temporary, LocalDateTime expiresAt) {
        return Boolean.TRUE.equals(temporary) && expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
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
