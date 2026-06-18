package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vdt.mini.shared_lib.document.InboundEndpointDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void constructor_shouldNotCreateOrReadIdentityFile() throws Exception {
        Path identityPath = tempDir.resolve("user-service-security.json");
        objectMapper.writeValue(identityPath.toFile(), Map.of(
                "serviceId", "service-from-file",
                "name", "custom-user-service",
                "baseUrl", "http://custom:8081",
                "description", "Custom description",
                "inbounds", Map.of(),
                "outbounds", Map.of()
        ));

        IdentityManager identityManager = new IdentityManager("default", "my-service");
        IdentityManager.ServiceMetadata metadata = identityManager.ensureServiceMetadata(
                "user-service", "http://localhost:8081", "Quản lí account người dùng");

        assertThat(identityManager.getOrCreateServiceId()).isEqualTo(SecurityIdGenerator.serviceId("default", "my-service"));
        assertThat(metadata.serviceName()).isEqualTo("user-service");
        assertThat(metadata.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(metadata.description()).isEqualTo("Quản lí account người dùng");
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = objectMapper.readValue(identityPath.toFile(), Map.class);
        assertThat(saved).containsEntry("serviceId", "service-from-file")
                .containsEntry("name", "custom-user-service")
                .containsEntry("baseUrl", "http://custom:8081")
                .containsEntry("description", "Custom description");
    }

    @Test
    void ensureServiceMetadata_shouldNotPersistDefaultsWhenIdentityFileIsMissing() {
        Path identityPath = tempDir.resolve("missing-user-service-security.json");
        IdentityManager identityManager = new IdentityManager("default", "my-service");

        IdentityManager.ServiceMetadata metadata = identityManager.ensureServiceMetadata(
                "user-service", "http://localhost:8081", "Quản lí account người dùng");

        assertThat(metadata.serviceName()).isEqualTo("user-service");
        assertThat(metadata.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(metadata.description()).isEqualTo("Quản lí account người dùng");
        assertThat(Files.exists(identityPath)).isFalse();
    }

    @Test
    void saveDeterministicMetadata_shouldUpdateOnlyInMemory() {
        Path identityPath = tempDir.resolve("user-service-security.json");
        IdentityManager identityManager = new IdentityManager("default", "my-service");
        String serviceId = SecurityIdGenerator.serviceId("mini-project", "user-service");
        String inboundId = SecurityIdGenerator.endpointId(serviceId, "INBOUND", "HTTP", "PUT", "/users/update-v2", "");

        identityManager.saveDeterministicMetadata(
                serviceId,
                "user-service",
                "http://localhost:8081",
                "User Service",
                List.of(new InboundEndpointDTO(
                        inboundId, "update user", "/users/update-v2", "", "PUT", "HTTP", "", true)),
                List.of());

        assertThat(identityManager.getKnownInbounds()).containsKey("HTTP_PUT_/users/update-v2");
        assertThat(Files.exists(identityPath)).isFalse();
    }
}
