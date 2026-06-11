package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.AccessPermissionDTO;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeChangePayloadDTO;
import vdt.mini.shared_lib.document.RuntimeManifestDTO;
import vdt.mini.shared_lib.document.RuntimeTombstoneDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.document.ServiceAuthConfigsSnapshotDTO;
import vdt.mini.shared_lib.document.ServiceClientsSnapshotDTO;
import vdt.mini.shared_lib.document.ServicePermissionsSnapshotDTO;
import vdt.mini.shared_lib.document.SettingsChangeMessage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
                    inboundSettings.put(id, dto);
                    log.debug("Loaded inbound settings from Redis: endpointId={} enabled={}", id, dto.getEnabled());
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
        String eventType = message.getEventType();
        if ("SERVICE_SNAPSHOT_REFRESHED".equals(eventType)) {
            if (isStale(eventVersionKey(message), message.getVersion(), eventType, message.getServiceId())) {
                return;
            }
            pollRuntimeFromRedis(message.getServiceId(), lastInboundIds, lastOutboundIds, "snapshot refresh");
            markVersion(message);
            return;
        }
        RuntimeChangePayloadDTO payload = message.getPayload();
        if (payload == null) {
            log.warn("Redis runtime legacy/null payload event received; applying eviction fallback eventType={} serviceId={} version={}",
                    eventType, message.getServiceId(), message.getVersion());
            applyLegacyEviction(message);
            markVersion(message);
            return;
        }
        boolean clientRemoved = false;
        if (payload.getClient() != null) {
            if ((eventType != null && (eventType.endsWith("DISABLED") || eventType.endsWith("REVOKED")))
                    || !Boolean.TRUE.equals(payload.getClient().getEnabled()) || !Boolean.TRUE.equals(payload.getClient().getActive())) {
                removeClientCascade(message.getServiceId(), payload.getClient().getClientId(), message.getVersion(), eventType);
                clientRemoved = true;
            } else {
                upsertClient(message.getServiceId(), payload.getClient(), message.getVersion(), eventType);
            }
        }
        if (!clientRemoved) {
            for (AuthConfigRuntimeDTO auth : safeList(payload.getAuthConfigs())) {
                upsertAuth(auth, message.getVersion(), eventType);
            }
            for (PermissionRuntimeDTO permission : safeList(payload.getPermissions())) {
                upsertPermission(permission, message.getVersion(), eventType);
            }
        }
        for (RuntimeTombstoneDTO tombstone : safeList(payload.getTombstones())) {
            applyTombstone(message.getServiceId(), tombstone, message.getVersion(), eventType);
        }
        log.info("Redis runtime event applied direct eventType={} serviceId={} version={}", eventType, message.getServiceId(), message.getVersion());
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
        if (value != null && isAuthExpired(value)) {
            removeAuth(serviceId, clientId, value.getAuthConfigId(), System.currentTimeMillis(), "AUTH_CONFIG_EXPIRED");
            log.info("Runtime auth expired and removed serviceId={} endpointId={} clientId={} authConfigId={}",
                    serviceId, endpointId, clientId, value.getAuthConfigId());
            value = null;
        }
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
            if (auth.getClientId() != null && Boolean.TRUE.equals(auth.getEnabled()) && !isAuthExpired(auth)) {
                result.put(auth.getClientId(), auth);
            } else if (auth.getClientId() != null && isAuthExpired(auth)) {
                log.info("Runtime auth snapshot item ignored because expired serviceId={} clientId={} authConfigId={}",
                        serviceId, auth.getClientId(), auth.getAuthConfigId());
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
            if (permission.getInboundEndpointId() != null && permission.getClientId() != null) {
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
        String operation = inferSettingsOperation(message);
        String versionKey = settingsVersionKey(message.getType(), message.getEndpointId());
        if (isStale(versionKey, message.getVersion(), "SETTINGS_" + operation, message.getServiceId())) {
            return;
        }
        if ("INBOUND".equals(message.getType())) {
            InboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), InboundSettingsDTO.class);
            if ("REMOVE".equals(operation)) {
                inboundSettings.remove(message.getEndpointId());
                markVersion(versionKey, message.getVersion());
                log.info("Removed inbound settings from pub/sub operation=REMOVE endpointId={}", message.getEndpointId());
            } else if (config != null) {
                inboundSettings.put(message.getEndpointId(), config);
                log.info("Updated inbound settings from pub/sub: endpointId={} serviceId={} enabled={}",
                        message.getEndpointId(), message.getServiceId(), config.getEnabled());
                markVersion(versionKey, message.getVersion());
            }
        } else if ("OUTBOUND".equals(message.getType())) {
            OutboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), OutboundSettingsDTO.class);
            if ("REMOVE".equals(operation)) {
                outboundSettings.remove(message.getEndpointId());
                markVersion(versionKey, message.getVersion());
                log.info("Removed outbound settings from pub/sub operation=REMOVE endpointId={}", message.getEndpointId());
            } else if (config != null) {
                if (Boolean.FALSE.equals(config.getEnabled())) {
                    outboundSettings.remove(message.getEndpointId());
                    log.info("Removed outbound settings from pub/sub: endpointId={}", message.getEndpointId());
                } else {
                    outboundSettings.put(message.getEndpointId(), config);
                    log.info("Updated outbound settings from pub/sub: endpointId={} serviceId={}", message.getEndpointId(), message.getServiceId());
                }
                markVersion(versionKey, message.getVersion());
            }
        } else {
            log.warn("Unknown settings change message type: {}", message.getType());
        }
    }

    private boolean isStale(String key, Long incoming, String eventType, String serviceId) {
        if (incoming == null) {
            return false;
        }
        Long current = lastVersionByKey.get(key);
        if (current != null && incoming <= current) {
            log.info("Redis runtime event skipped stale eventType={} serviceId={} versionKey={} incomingVersion={} currentVersion={}",
                    eventType, serviceId, key, incoming, current);
            return true;
        }
        return false;
    }

    private void markVersion(SecurityRuntimeChangeMessage message) {
        if (message.getVersion() != null) {
            lastVersionByKey.put(eventVersionKey(message), message.getVersion());
        }
    }

    private void markVersion(String key, Long version) {
        if (version != null) {
            lastVersionByKey.put(key, version);
        }
    }

    private String eventVersionKey(SecurityRuntimeChangeMessage message) {
        return String.join(":", message.getServiceId(), nullToBlank(message.getEventType()), nullToBlank(message.getEndpointId()),
                nullToBlank(message.getClientId()), nullToBlank(message.getAuthConfigId()), nullToBlank(message.getPermissionId()));
    }

    private String nullToBlank(String value) { return value == null ? "" : value; }

    private void applyLegacyEviction(SecurityRuntimeChangeMessage message) {
        String eventType = message.getEventType();
        if (eventType != null && eventType.startsWith("CLIENT_")) {
            removeClientCascade(message.getServiceId(), message.getClientId(), message.getVersion(), eventType);
        } else if (eventType != null && eventType.startsWith("AUTH_CONFIG_")) {
            removeAuth(message.getServiceId(), message.getClientId(), message.getAuthConfigId(), message.getVersion(), eventType);
        } else if (eventType != null && eventType.startsWith("PERMISSION_")) {
            removePermission(message.getServiceId(), message.getEndpointId(), message.getClientId(), message.getPermissionId(), message.getVersion(), eventType);
        } else {
            log.warn("Unknown Redis runtime event type ignored eventType={} serviceId={} version={}",
                    eventType, message.getServiceId(), message.getVersion());
        }
    }

    private void upsertClient(String serviceId, ClientRuntimeDTO client, Long messageVersion, String eventType) {
        String key = clientVersionKey(serviceId, client.getClientId());
        Long version = resolveVersion(messageVersion, client.getVersion());
        if (isStale(key, version, eventType, serviceId)) { return; }
        serviceMap(clientsByServiceThenClient, serviceId).put(client.getClientId(), client);
        if (client.getClientKey() != null && !client.getClientKey().isBlank()) {
            serviceMap(clientKeyToClientIdByService, serviceId).put(client.getClientKey(), client.getClientId());
        }
        markVersion(key, version);
        log.info("Runtime client applied direct serviceId={} clientId={} version={}", serviceId, client.getClientId(), version);
    }

    private void removeClientCascade(String serviceId, String clientId, Long version, String eventType) {
        if (clientId == null) { return; }
        String key = clientVersionKey(serviceId, clientId);
        if (isStale(key, version, eventType, serviceId)) { return; }
        evictClientRelated(serviceId, clientId);
        markVersion(key, version);
        log.info("Runtime client removed with cascade serviceId={} clientId={} version={}", serviceId, clientId, version);
    }

    private void upsertAuth(AuthConfigRuntimeDTO auth, Long messageVersion, String eventType) {
        String serviceId = auth.getServiceId();
        String clientId = auth.getClientId();
        if (serviceId == null || clientId == null) { return; }
        Long version = resolveVersion(messageVersion, auth.getVersion());
        String key = authVersionKey(serviceId, clientId);
        if (isStale(key, version, eventType, serviceId)) { return; }
        if (!Boolean.TRUE.equals(auth.getEnabled()) || isAuthExpired(auth)) {
            removeAuth(serviceId, clientId, auth.getAuthConfigId(), version, isAuthExpired(auth) ? "AUTH_CONFIG_EXPIRED" : eventType);
            return;
        }
        authByServiceThenClient.computeIfAbsent(serviceId, ignored -> new ConcurrentHashMap<>()).put(clientId, auth);
        markVersion(key, version);
        log.info("Runtime auth applied direct serviceId={} clientId={} authConfigId={} version={}", serviceId, clientId, auth.getAuthConfigId(), version);
    }

    private void removeAuth(String serviceId, String clientId, String authConfigId, Long version, String eventType) {
        if (serviceId == null) { return; }
        if (clientId != null) {
            String key = authVersionKey(serviceId, clientId);
            if (isStale(key, version, eventType, serviceId)) { return; }
            serviceMap(authByServiceThenClient, serviceId).remove(clientId);
            markVersion(key, version);
        } else if (authConfigId != null) {
            serviceMap(authByServiceThenClient, serviceId).entrySet().removeIf(entry -> authConfigId.equals(entry.getValue().getAuthConfigId()));
        }
        log.info("Runtime auth removed serviceId={} clientId={} authConfigId={} eventType={} version={}", serviceId, clientId, authConfigId, eventType, version);
    }

    private void upsertPermission(PermissionRuntimeDTO permission, Long messageVersion, String eventType) {
        String serviceId = permission.getServiceId();
        String endpointId = permission.getInboundEndpointId();
        String clientId = permission.getClientId();
        if (serviceId == null || endpointId == null || clientId == null) { return; }
        Long version = resolveVersion(messageVersion, permission.getVersion());
        String key = permissionVersionKey(serviceId, endpointId, clientId);
        if (isStale(key, version, eventType, serviceId)) { return; }
        serviceMap(permissionsByServiceEndpointClient, serviceId).put(permissionKey(endpointId, clientId), permission);
        markVersion(key, version);
        log.info("Runtime permission applied direct serviceId={} endpointId={} clientId={} permissionId={} enabled={} version={}",
                serviceId, endpointId, clientId, permission.getPermissionId(), permission.getEnabled(), version);
    }

    private void removePermission(String serviceId, String endpointId, String clientId, String permissionId, Long version, String eventType) {
        if (serviceId == null) { return; }
        if (endpointId != null && clientId != null) {
            String key = permissionVersionKey(serviceId, endpointId, clientId);
            if (isStale(key, version, eventType, serviceId)) { return; }
            serviceMap(permissionsByServiceEndpointClient, serviceId).remove(permissionKey(endpointId, clientId));
            removePermissionFromInboundSettings(endpointId, clientId, permissionId);
            markVersion(key, version);
        } else if (permissionId != null) {
            serviceMap(permissionsByServiceEndpointClient, serviceId).entrySet().removeIf(entry -> permissionId.equals(entry.getValue().getPermissionId()));
            removePermissionFromInboundSettings(null, null, permissionId);
        }
        log.info("Runtime permission tombstone removed serviceId={} endpointId={} clientId={} permissionId={} eventType={} version={}",
                serviceId, endpointId, clientId, permissionId, eventType, version);
    }

    private void removePermissionFromInboundSettings(String endpointId, String clientId, String permissionId) {
        inboundSettings.forEach((settingsEndpointId, settings) -> {
            if (settings == null || settings.getPermissions() == null) {
                return;
            }
            if (endpointId != null && !endpointId.equals(settingsEndpointId)) {
                return;
            }
            settings.setPermissions(settings.getPermissions().stream()
                    .filter(permission -> !matchesPermission(permission, settingsEndpointId, clientId, permissionId))
                    .toList());
        });
    }

    private boolean matchesPermission(AccessPermissionDTO permission, String endpointId, String clientId, String permissionId) {
        if (permission == null) {
            return false;
        }
        if (permissionId != null) {
            return permissionId.equals(permission.getPermissionId());
        }
        boolean endpointMatches = permission.getInboundEndpointId() == null || permission.getInboundEndpointId().equals(endpointId);
        boolean clientMatches = clientId != null && clientId.equals(permission.getClientId());
        return endpointMatches && clientMatches;
    }

    private void applyTombstone(String fallbackServiceId, RuntimeTombstoneDTO tombstone, Long version, String eventType) {
        String serviceId = tombstone.getServiceId() != null ? tombstone.getServiceId() : fallbackServiceId;
        if ("CLIENT".equals(tombstone.getResourceType())) {
            removeClientCascade(serviceId, tombstone.getClientId(), version, eventType);
        } else if ("AUTH_CONFIG".equals(tombstone.getResourceType())) {
            removeAuth(serviceId, tombstone.getClientId(), tombstone.getAuthConfigId(), version, eventType);
        } else if ("PERMISSION".equals(tombstone.getResourceType())) {
            removePermission(serviceId, tombstone.getEndpointId(), tombstone.getClientId(), tombstone.getPermissionId(), version, eventType);
        } else {
            log.warn("Unknown runtime tombstone ignored resourceType={} serviceId={} eventType={}", tombstone.getResourceType(), serviceId, eventType);
        }
    }

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

    private String inferSettingsOperation(SettingsChangeMessage message) {
        if (message.getOperation() != null && !message.getOperation().isBlank()) {
            return message.getOperation();
        }
        if (message.getConfig() == null) {
            return "REMOVE";
        }
        try {
            if ("INBOUND".equals(message.getType())) {
                InboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), InboundSettingsDTO.class);
                return Boolean.FALSE.equals(config.getEnabled()) ? "REMOVE" : "UPSERT";
            }
            if ("OUTBOUND".equals(message.getType())) {
                OutboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), OutboundSettingsDTO.class);
                return Boolean.FALSE.equals(config.getEnabled()) ? "REMOVE" : "UPSERT";
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Could not infer legacy settings operation type={} endpointId={}", message.getType(), message.getEndpointId(), ex);
        }
        return "UPSERT";
    }

    private boolean isAuthExpired(AuthConfigRuntimeDTO auth) {
        if (auth == null || auth.getExpiresAt() == null || auth.getExpiresAt().isBlank()) {
            return false;
        }
        try {
            return LocalDateTime.parse(auth.getExpiresAt()).isBefore(LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Runtime auth expiresAt parse failed authConfigId={} expiresAt={}", auth.getAuthConfigId(), auth.getExpiresAt(), ex);
            return false;
        }
    }

    private Long resolveVersion(Long messageVersion, Long payloadVersion) {
        return messageVersion != null ? messageVersion : payloadVersion;
    }

    private String settingsVersionKey(String type, String endpointId) {
        return "settings:" + nullToBlank(type) + ":" + nullToBlank(endpointId);
    }

    private String clientVersionKey(String serviceId, String clientId) {
        return "client:" + nullToBlank(serviceId) + ":" + nullToBlank(clientId);
    }

    private String authVersionKey(String serviceId, String clientId) {
        return "auth:" + nullToBlank(serviceId) + ":" + nullToBlank(clientId);
    }

    private String permissionVersionKey(String serviceId, String endpointId, String clientId) {
        return "permission:" + nullToBlank(serviceId) + ":" + nullToBlank(endpointId) + ":" + nullToBlank(clientId);
    }

    private String permissionKey(String endpointId, String clientId) {
        return endpointId + ":" + clientId;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
