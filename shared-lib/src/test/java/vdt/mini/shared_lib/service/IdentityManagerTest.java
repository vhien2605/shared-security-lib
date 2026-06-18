package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void ensureServiceMetadata_shouldPersistDefaultsWhenMissing() throws Exception {
        Path identityPath = tempDir.resolve("user-service-security.json");
        IdentityManager identityManager = new IdentityManager(identityPath.toString());

        IdentityManager.ServiceMetadata metadata = identityManager.ensureServiceMetadata(
                "user-service", "http://localhost:8081", "Quản lí account người dùng");

        assertThat(metadata.serviceName()).isEqualTo("user-service");
        assertThat(metadata.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(metadata.description()).isEqualTo("Quản lí account người dùng");

        Map<String, Object> saved = objectMapper.readValue(identityPath.toFile(), new TypeReference<>() {});
        assertThat(saved).containsEntry("name", "user-service")
                .containsEntry("baseUrl", "http://localhost:8081")
                .containsEntry("description", "Quản lí account người dùng");
    }

    @Test
    void ensureServiceMetadata_shouldPreferConfiguredJsonValues() throws Exception {
        Path identityPath = tempDir.resolve("user-service-security.json");
        objectMapper.writeValue(identityPath.toFile(), Map.of(
                "serviceId", "service-1",
                "name", "custom-user-service",
                "baseUrl", "http://custom:8081",
                "description", "Custom description",
                "inbounds", Map.of(),
                "outbounds", Map.of()
        ));

        IdentityManager identityManager = new IdentityManager(identityPath.toString());
        IdentityManager.ServiceMetadata metadata = identityManager.ensureServiceMetadata(
                "user-service", "http://localhost:8081", "Quản lí account người dùng");

        assertThat(metadata.serviceName()).isEqualTo("custom-user-service");
        assertThat(metadata.baseUrl()).isEqualTo("http://custom:8081");
        assertThat(metadata.description()).isEqualTo("Custom description");
    }
}
