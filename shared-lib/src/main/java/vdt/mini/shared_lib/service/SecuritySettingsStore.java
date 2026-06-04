package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeManifestDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.document.ServiceAuthConfigsSnapshotDTO;
import vdt.mini.shared_lib.document.ServiceClientsSnapshotDTO;
import vdt.mini.shared_lib.document.ServicePermissionsSnapshotDTO;
import vdt.mini.shared_lib.document.SettingsChangeMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SecuritySettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SecuritySettingsStore.class);

    private final ConcurrentHashMap<String, InboundSettingsDTO> inboundSettings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OutboundSettingsDTO> outboundSettings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ClientRuntimeDTO>> clientsByServiceThenClient = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> clientKeyToClientIdByService = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AuthConfigRuntimeDTO>> authByServiceThenClient = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PermissionRuntimeDTO>> permissionsByServiceEndpointClient = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastVersionByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSnapshotVersionByService = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastRefreshAtByService = new ConcurrentHashMap<>();
    private volatile List<String> lastInboundIds = List.of();
    private volatile List<String> lastOutboundIds = List.of();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecuritySettingsStore(
            @Qualifier("securityRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public InboundSettingsDTO getInboundSettings(String endpointId) {
        return inboundSettings.get(endpointId);
    }

    public OutboundSettingsDTO getOutboundSettings(String endpointId) {
        return outboundSettings.get(endpointId);
    }

    public void pollFromRedis(List<String> inboundIds, List<String> outboundIds) {
        for (String id : inboundIds) {
            try {
                String json = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.inboundSettings(id));
                if (json != null) {
                    InboundSettingsDTO dto = objectMapper.readValue(json, InboundSettingsDTO.class);
                    if (Boolean.FALSE.equals(dto.getEnabled())) {
                        inboundSettings.remove(id);
                    } else {
                        inboundSettings.put(id, dto);
                        log.debug("Loaded inbound settings from Redis: endpointId={}", id);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to poll inbound settings from Redis for endpointId={}", id, e);
            }
        }
        for (String id : outboundIds) {
            try {
                String json = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.outboundSettings(id));
                if (json != null) {
                    OutboundSettingsDTO dto = objectMapper.readValue(json, OutboundSettingsDTO.class);
                    if (Boolean.FALSE.equals(dto.getEnabled())) {
                        outboundSettings.remove(id);
                    } else {
                        outboundSettings.put(id, dto);
                        log.debug("Loaded outbound settings from Redis: endpointId={}", id);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to poll outbound settings from Redis for endpointId={}", id, e);
            }
        }
    }

    public void pollRuntimeFromRedis(String serviceId, List<String> inboundIds, List<String> outboundIds) {
        pollRuntimeFromRedis(serviceId, inboundIds, outboundIds, "startup");
    }

    private void pollRuntimeFromRedis(String serviceId, List<String> inboundIds, List<String> outboundIds, String context) {
        if (serviceId == null || serviceId.isBlank()) {
            log.warn("Redis runtime {} poll skipped because serviceId is blank", context);
            return;
        }
        List<String> safeInboundIds = inboundIds == null ? List.of() : List.copyOf(inboundIds);
        List<String> safeOutboundIds = outboundIds == null ? List.of() : List.copyOf(outboundIds);
        lastInboundIds = safeInboundIds;
        lastOutboundIds = safeOutboundIds;
        log.info("Redis runtime {} poll started serviceId={} inboundCount={} outboundCount={}",
                context, serviceId, safeInboundIds.size(), safeOutboundIds.size());
        pollFromRedis(safeInboundIds, safeOutboundIds);
        try {
            String manifestJson = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.manifest(serviceId));
            if (manifestJson == null || manifestJson.isBlank()) {
                log.info("Redis runtime manifest missing serviceId={}", serviceId);
                lastRefreshAtByService.put(serviceId, Instant.now());
                return;
            }
            RuntimeManifestDTO manifest = objectMapper.readValue(manifestJson, RuntimeManifestDTO.class);
            Long version = manifest.getVersion() == null ? System.currentTimeMillis() : manifest.getVersion();
            log.info("Redis runtime manifest loaded serviceId={} version={} clientCount={} authConfigCount={} permissionCount={}",
                    serviceId, version, manifest.getClientCount(), manifest.getAuthConfigCount(), manifest.getPermissionCount());

            Map<String, ClientRuntimeDTO> clients = loadClients(serviceId);
            Map<String, AuthConfigRuntimeDTO> authConfigs = loadAuthConfigs(serviceId);
            Map<String, PermissionRuntimeDTO> permissions = loadPermissions(serviceId);
            clientsByServiceThenClient.put(serviceId, new ConcurrentHashMap<>(clients));
            clientKeyToClientIdByService.put(serviceId, buildClientKeyIndex(clients));
            authByServiceThenClient.put(serviceId, new ConcurrentHashMap<>(authConfigs));
            permissionsByServiceEndpointClient.put(serviceId, new ConcurrentHashMap<>(permissions));
            lastSnapshotVersionByService.put(serviceId, version);
            lastRefreshAtByService.put(serviceId, Instant.now());
            log.info("Redis runtime cache replacement completed serviceId={} lastSnapshotVersion={} clientCount={} authConfigCount={} permissionCount={}",
                    serviceId, version, clients.size(), authConfigs.size(), permissions.size());
        } catch (Exception ex) {
            log.warn("Failed to poll Redis runtime snapshots serviceId={}", serviceId, ex);
        }
        log.info("Redis runtime {} poll completed serviceId={}", context, serviceId);
    }

    public void onRuntimeChange(SecurityRuntimeChangeMessage message) {
        if (message == null || message.getServiceId() == null || message.getServiceId().isBlank()) {
            log.warn("Received invalid Redis runtime event");
            return;
        }
        log.info("Redis runtime event received eventType={} serviceId={} version={} endpointId={} clientId={} authConfigId={} permissionId={}",
                message.getEventType(), message.getServiceId(), message.getVersion(), message.getEndpointId(), message.getClientId(),
                message.getAuthConfigId(), message.getPermissionId());
        if (isStale(message)) {
            return;
        }
        String eventType = message.getEventType();
        if ("SERVICE_SNAPSHOT_REFRESHED".equals(eventType)) {
            pollRuntimeFromRedis(message.getServiceId(), lastInboundIds, lastOutboundIds, "snapshot refresh");
            markVersion(message);
            return;
        }
        if (eventType != null && eventType.startsWith("CLIENT_")) {
            evictClientRelated(message.getServiceId(), message.getClientId());
        } else if (eventType != null && eventType.startsWith("AUTH_CONFIG_")) {
            evictAuthForClient(message.getServiceId(), message.getClientId());
        } else if (eventType != null && eventType.startsWith("PERMISSION_")) {
            evictPermission(message.getServiceId(), message.getEndpointId(), message.getClientId());
        } else {
            log.warn("Unknown Redis runtime event type ignored eventType={} serviceId={} version={}",
                    eventType, message.getServiceId(), message.getVersion());
        }
        markVersion(message);
        log.info("Redis runtime event applied eventType={} serviceId={} version={}", eventType, message.getServiceId(), message.getVersion());
    }

    public Optional<ClientRuntimeDTO> getClient(String serviceId, String clientId) {
        ClientRuntimeDTO value = serviceMap(clientsByServiceThenClient, serviceId).get(clientId);
        if (value == null) {
            log.debug("Runtime client lookup miss serviceId={} clientId={}", serviceId, clientId);
        }
        return Optional.ofNullable(value);
    }

    public Optional<AuthConfigRuntimeDTO> getAuthConfig(String serviceId, String endpointId, String clientId) {
        AuthConfigRuntimeDTO value = serviceMap(authByServiceThenClient, serviceId).get(clientId);
        if (value == null) {
            log.debug("Runtime auth lookup miss serviceId={} endpointId={} clientId={}", serviceId, endpointId, clientId);
        }
        return Optional.ofNullable(value);
    }

    public Optional<String> resolveClientId(String serviceId, String clientKey) {
        if (serviceId == null || serviceId.isBlank() || clientKey == null || clientKey.isBlank()) {
            return Optional.empty();
        }
        String clientId = serviceMap(clientKeyToClientIdByService, serviceId).get(clientKey);
        if (clientId == null) {
            log.debug("Runtime clientKey lookup miss serviceId={} clientKey={}", serviceId, clientKey);
        }
        return Optional.ofNullable(clientId);
    }

    public Optional<AuthConfigRuntimeDTO> getAuthConfigByClientKey(String serviceId, String clientKey) {
        return resolveClientId(serviceId, clientKey)
                .flatMap(clientId -> getAuthConfig(serviceId, null, clientId));
    }

    public Optional<PermissionRuntimeDTO> getPermission(String serviceId, String inboundEndpointId, String clientId) {
        PermissionRuntimeDTO value = serviceMap(permissionsByServiceEndpointClient, serviceId).get(permissionKey(inboundEndpointId, clientId));
        if (value == null) {
            log.debug("Runtime permission lookup miss serviceId={} endpointId={} clientId={}", serviceId, inboundEndpointId, clientId);
        }
        return Optional.ofNullable(value);
    }

    public boolean hasInboundPermission(String serviceId, String inboundEndpointId, String clientId) {
        return getPermission(serviceId, inboundEndpointId, clientId)
                .filter(permission -> Boolean.TRUE.equals(permission.getEnabled()))
                .isPresent();
    }

    public void refreshService(String serviceId) {
        pollRuntimeFromRedis(serviceId, lastInboundIds, lastOutboundIds, "manual refresh");
    }

    public Instant getLastRefreshAt(String serviceId) {
        return lastRefreshAtByService.get(serviceId);
    }

    public boolean isSnapshotStale(String serviceId, Duration maxAge) {
        Instant lastRefreshAt = lastRefreshAtByService.get(serviceId);
        return lastRefreshAt == null || (maxAge != null && lastRefreshAt.plus(maxAge).isBefore(Instant.now()));
    }

    private Map<String, ClientRuntimeDTO> loadClients(String serviceId) throws Exception {
        String json = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.clients(serviceId));
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        ServiceClientsSnapshotDTO snapshot = objectMapper.readValue(json, ServiceClientsSnapshotDTO.class);
        Map<String, ClientRuntimeDTO> result = new HashMap<>();
        for (ClientRuntimeDTO client : safeList(snapshot.getClients())) {
            if (client.getClientId() != null && Boolean.TRUE.equals(client.getEnabled())) {
                result.put(client.getClientId(), client);
            }
        }
        return result;
    }

    private Map<String, AuthConfigRuntimeDTO> loadAuthConfigs(String serviceId) throws Exception {
        String json = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.authConfigs(serviceId));
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        ServiceAuthConfigsSnapshotDTO snapshot = objectMapper.readValue(json, ServiceAuthConfigsSnapshotDTO.class);
        Map<String, AuthConfigRuntimeDTO> result = new HashMap<>();
        for (AuthConfigRuntimeDTO auth : safeList(snapshot.getAuthConfigs())) {
            if (auth.getClientId() != null && Boolean.TRUE.equals(auth.getEnabled())) {
                result.put(auth.getClientId(), auth);
            }
        }
        return result;
    }

    private Map<String, PermissionRuntimeDTO> loadPermissions(String serviceId) throws Exception {
        String json = redisTemplate.opsForValue().get(RedisSecurityRuntimeKeys.permissions(serviceId));
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        ServicePermissionsSnapshotDTO snapshot = objectMapper.readValue(json, ServicePermissionsSnapshotDTO.class);
        Map<String, PermissionRuntimeDTO> result = new HashMap<>();
        for (PermissionRuntimeDTO permission : safeList(snapshot.getPermissions())) {
            if (permission.getInboundEndpointId() != null && permission.getClientId() != null && Boolean.TRUE.equals(permission.getEnabled())) {
                result.put(permissionKey(permission.getInboundEndpointId(), permission.getClientId()), permission);
            }
        }
        return result;
    }

    public void onSettingsChange(SettingsChangeMessage message) {
        if (message == null) {
            log.warn("Received null settings change message");
            return;
        }
        if ("INBOUND".equals(message.getType())) {
            InboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), InboundSettingsDTO.class);
            if (config != null) {
                if (Boolean.FALSE.equals(config.getEnabled())) {
                    inboundSettings.remove(message.getEndpointId());
                    log.info("Removed inbound settings from pub/sub: endpointId={}", message.getEndpointId());
                } else {
                    inboundSettings.put(message.getEndpointId(), config);
                    log.info("Updated inbound settings from pub/sub: endpointId={} serviceId={}", message.getEndpointId(), message.getServiceId());
                }
            }
        } else if ("OUTBOUND".equals(message.getType())) {
            OutboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), OutboundSettingsDTO.class);
            if (config != null) {
                if (Boolean.FALSE.equals(config.getEnabled())) {
                    outboundSettings.remove(message.getEndpointId());
                    log.info("Removed outbound settings from pub/sub: endpointId={}", message.getEndpointId());
                } else {
                    outboundSettings.put(message.getEndpointId(), config);
                    log.info("Updated outbound settings from pub/sub: endpointId={} serviceId={}", message.getEndpointId(), message.getServiceId());
                }
            }
        } else {
            log.warn("Unknown settings change message type: {}", message.getType());
        }
    }

    private boolean isStale(SecurityRuntimeChangeMessage message) {
        Long incoming = message.getVersion();
        if (incoming == null) {
            return false;
        }
        String key = eventVersionKey(message);
        Long current = lastVersionByKey.get(key);
        if (current != null && incoming <= current) {
            log.info("Redis runtime event skipped stale eventType={} serviceId={} incomingVersion={} currentVersion={}",
                    message.getEventType(), message.getServiceId(), incoming, current);
            return true;
        }
        return false;
    }

    private void markVersion(SecurityRuntimeChangeMessage message) {
        if (message.getVersion() != null) {
            lastVersionByKey.put(eventVersionKey(message), message.getVersion());
        }
    }

    private String eventVersionKey(SecurityRuntimeChangeMessage message) {
        return String.join(":", message.getServiceId(), nullToBlank(message.getEventType()), nullToBlank(message.getEndpointId()),
                nullToBlank(message.getClientId()), nullToBlank(message.getAuthConfigId()), nullToBlank(message.getPermissionId()));
    }

    private String nullToBlank(String value) { return value == null ? "" : value; }

    private void evictClientRelated(String serviceId, String clientId) {
        if (clientId == null) { return; }
        ClientRuntimeDTO removedClient = serviceMap(clientsByServiceThenClient, serviceId).remove(clientId);
        ConcurrentHashMap<String, String> clientKeyIndex = serviceMap(clientKeyToClientIdByService, serviceId);
        if (removedClient != null && removedClient.getClientKey() != null) {
            clientKeyIndex.remove(removedClient.getClientKey());
        } else {
            clientKeyIndex.entrySet().removeIf(entry -> clientId.equals(entry.getValue()));
        }
        evictAuthForClient(serviceId, clientId);
        serviceMap(permissionsByServiceEndpointClient, serviceId).entrySet().removeIf(entry -> entry.getKey().endsWith(":" + clientId));
    }

    private void evictAuthForClient(String serviceId, String clientId) {
        if (clientId != null) {
            serviceMap(authByServiceThenClient, serviceId).remove(clientId);
        }
    }

    private void evictPermission(String serviceId, String endpointId, String clientId) {
        if (endpointId != null && clientId != null) {
            serviceMap(permissionsByServiceEndpointClient, serviceId).remove(permissionKey(endpointId, clientId));
        }
    }

    private <T> ConcurrentHashMap<String, T> serviceMap(ConcurrentHashMap<String, ConcurrentHashMap<String, T>> source, String serviceId) {
        return source.computeIfAbsent(serviceId, ignored -> new ConcurrentHashMap<>());
    }

    private ConcurrentHashMap<String, String> buildClientKeyIndex(Map<String, ClientRuntimeDTO> clients) {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
        for (ClientRuntimeDTO client : clients.values()) {
            if (client.getClientKey() != null && !client.getClientKey().isBlank() && client.getClientId() != null) {
                result.put(client.getClientKey(), client.getClientId());
            }
        }
        return result;
    }

    private String permissionKey(String endpointId, String clientId) {
        return endpointId + ":" + clientId;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
