package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeManifestDTO;
import vdt.mini.shared_lib.document.RuntimeChangePayloadDTO;
import vdt.mini.shared_lib.document.RuntimeTombstoneDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.document.ServiceAuthConfigsSnapshotDTO;
import vdt.mini.shared_lib.document.ServiceClientsSnapshotDTO;
import vdt.mini.shared_lib.document.ServicePermissionsSnapshotDTO;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecuritySettingsStoreTest {
    private static final String SERVICE_ID = "service-1";
    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_KEY = "client-key-1";
    private static final String ENDPOINT_ID = "endpoint-1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SecuritySettingsStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        store = new SecuritySettingsStore(redisTemplate, objectMapper);
    }

    @Test
    void onRuntimeChange_shouldPollRuntimeSnapshots_whenServiceSnapshotRefreshed() throws Exception {
        arrangeRuntimeSnapshot();

        store.onRuntimeChange(runtimeEvent("SERVICE_SNAPSHOT_REFRESHED", 2L));

        verify(valueOperations).get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID));
        assertThat(store.resolveClientId(SERVICE_ID, CLIENT_KEY)).contains(CLIENT_ID);
        assertThat(store.getAuthConfigByClientKey(SERVICE_ID, CLIENT_KEY))
                .map(AuthConfigRuntimeDTO::getCredentialHash)
                .contains("credential-hash");
    }

    @Test
    void onRuntimeChange_shouldEvictClientOnly_withoutFullPoll_whenClientEventReceived() throws Exception {
        arrangeRuntimeSnapshot();
        store.pollRuntimeFromRedis(SERVICE_ID, List.of(), List.of());
        reset(valueOperations);

        store.onRuntimeChange(runtimeEvent("CLIENT_CHANGED", 3L));

        verify(valueOperations, never()).get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID));
        assertThat(store.resolveClientId(SERVICE_ID, CLIENT_KEY)).isEmpty();
        assertThat(store.getClient(SERVICE_ID, CLIENT_ID)).isEmpty();
        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isEmpty();
    }

    @Test
    void onRuntimeChange_shouldEvictAuthOnly_withoutFullPoll_whenAuthConfigEventReceived() throws Exception {
        arrangeRuntimeSnapshot();
        store.pollRuntimeFromRedis(SERVICE_ID, List.of(), List.of());
        reset(valueOperations);

        store.onRuntimeChange(runtimeEvent("AUTH_CONFIG_CHANGED", 4L));

        verify(valueOperations, never()).get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID));
        assertThat(store.resolveClientId(SERVICE_ID, CLIENT_KEY)).contains(CLIENT_ID);
        assertThat(store.getClient(SERVICE_ID, CLIENT_ID)).isPresent();
        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isEmpty();
    }

    @Test
    void onRuntimeChange_shouldEvictPermissionOnly_withoutFullPoll_whenPermissionEventReceived() throws Exception {
        arrangeRuntimeSnapshot();
        store.pollRuntimeFromRedis(SERVICE_ID, List.of(), List.of());
        reset(valueOperations);

        store.onRuntimeChange(runtimeEvent("PERMISSION_DELETED", 5L));

        verify(valueOperations, never()).get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID));
        assertThat(store.getPermission(SERVICE_ID, ENDPOINT_ID, CLIENT_ID)).isEmpty();
        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isPresent();
    }

    @Test
    void onRuntimeChange_shouldIgnoreUnknownEvent_withoutFullPollOrCrash() {
        assertDoesNotThrow(() -> store.onRuntimeChange(runtimeEvent("INBOUND_SETTINGS_CHANGED", 6L)));

        verify(valueOperations, never()).get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID));
    }

    @Test
    void resolveClientIdAndGetAuthConfigByClientKey_shouldReturnEmpty_whenClientKeyUnknown() throws Exception {
        arrangeRuntimeSnapshot();
        store.pollRuntimeFromRedis(SERVICE_ID, List.of(), List.of());

        assertThat(store.resolveClientId(SERVICE_ID, "missing-client-key")).isEmpty();
        assertThat(store.getAuthConfigByClientKey(SERVICE_ID, "missing-client-key")).isEmpty();
    }

    @Test
    void authConfigRuntimeDto_shouldDeserializeCredentialHash() throws Exception {
        String json = """
                {
                  "authConfigId":"auth-1",
                  "serviceId":"service-1",
                  "clientId":"client-1",
                  "clientKey":"client-key-1",
                  "type":"API_KEY",
                  "credentialHash":"credential-hash",
                  "enabled":true
                }
                """;

        AuthConfigRuntimeDTO dto = objectMapper.readValue(json, AuthConfigRuntimeDTO.class);

        assertThat(dto.getCredentialHash()).isEqualTo("credential-hash");
    }

    @Test
    void onRuntimeChange_shouldApplyDirectPayloadAndIgnoreStaleTombstone() {
        RuntimeChangePayloadDTO payload = new RuntimeChangePayloadDTO(client(), List.of(authConfig()), List.of(permission()), List.of());
        store.onRuntimeChange(runtimeEvent("CLIENT_CHANGED", 10L, payload));

        assertThat(store.resolveClientId(SERVICE_ID, CLIENT_KEY)).contains(CLIENT_ID);
        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isPresent();
        assertThat(store.getPermission(SERVICE_ID, ENDPOINT_ID, CLIENT_ID)).isPresent();

        RuntimeChangePayloadDTO staleDelete = new RuntimeChangePayloadDTO(null, List.of(), List.of(),
                List.of(new RuntimeTombstoneDTO("AUTH_CONFIG", SERVICE_ID, null, CLIENT_ID, "auth-1", null, "DELETED")));
        store.onRuntimeChange(runtimeEvent("AUTH_CONFIG_DELETED", 9L, staleDelete));

        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isPresent();
    }

    @Test
    void getAuthConfig_shouldRemoveExpiredAuth() {
        AuthConfigRuntimeDTO expired = authConfig();
        expired.setExpiresAt("2020-01-01T00:00:00");
        store.onRuntimeChange(runtimeEvent("AUTH_CONFIG_CHANGED", 11L,
                new RuntimeChangePayloadDTO(null, List.of(expired), List.of(), List.of())));

        assertThat(store.getAuthConfig(SERVICE_ID, null, CLIENT_ID)).isEmpty();
    }

    private void arrangeRuntimeSnapshot() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisSecurityRuntimeKeys.manifest(SERVICE_ID))).thenReturn(objectMapper.writeValueAsString(
                new RuntimeManifestDTO(SERVICE_ID, 1L, "2026-06-04T00:00:00", 0, 0, 1, 1, 1, Map.of())));
        when(valueOperations.get(RedisSecurityRuntimeKeys.clients(SERVICE_ID))).thenReturn(objectMapper.writeValueAsString(
                new ServiceClientsSnapshotDTO(SERVICE_ID, 1L, List.of(client()))));
        when(valueOperations.get(RedisSecurityRuntimeKeys.authConfigs(SERVICE_ID))).thenReturn(objectMapper.writeValueAsString(
                new ServiceAuthConfigsSnapshotDTO(SERVICE_ID, 1L, List.of(authConfig()))));
        when(valueOperations.get(RedisSecurityRuntimeKeys.permissions(SERVICE_ID))).thenReturn(objectMapper.writeValueAsString(
                new ServicePermissionsSnapshotDTO(SERVICE_ID, 1L, List.of(permission()))));
    }

    private SecurityRuntimeChangeMessage runtimeEvent(String eventType, long version) {
        return runtimeEvent(eventType, version, null);
    }

    private SecurityRuntimeChangeMessage runtimeEvent(String eventType, long version, RuntimeChangePayloadDTO payload) {
        return new SecurityRuntimeChangeMessage("event-" + version, eventType, SERVICE_ID, ENDPOINT_ID, CLIENT_ID,
                "auth-1", "permission-1", List.of("runtimeSnapshot"), version, "2026-06-04T00:00:00", payload);
    }

    private ClientRuntimeDTO client() {
        return new ClientRuntimeDTO(CLIENT_ID, CLIENT_KEY, "Client One", "ACTIVE", true, true, null, null, 1L);
    }

    private AuthConfigRuntimeDTO authConfig() {
        return new AuthConfigRuntimeDTO("auth-1", SERVICE_ID, CLIENT_ID, CLIENT_KEY, "API_KEY", "api-ref",
                "credential-hash", null, null, null, null, true, "ACTIVE", 1L);
    }

    private PermissionRuntimeDTO permission() {
        return new PermissionRuntimeDTO("permission-1", SERVICE_ID, ENDPOINT_ID, CLIENT_ID, CLIENT_KEY, true, "ACTIVE", 1L);
    }
}
