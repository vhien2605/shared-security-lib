package vdt.mini.management_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSecurityRuntimeKeysTest {
    @Test
    void shouldBuildLegacyAndRuntimeKeys() {
        assertThat(RedisSecurityRuntimeKeys.inboundSettings("endpoint-1"))
                .isEqualTo("security:config:inbound:endpoint-1");
        assertThat(RedisSecurityRuntimeKeys.outboundSettings("endpoint-1"))
                .isEqualTo("security:config:outbound:endpoint-1");
        assertThat(RedisSecurityRuntimeKeys.legacySettingsChannel("service-1"))
                .isEqualTo("security:settings:service-1");
        assertThat(RedisSecurityRuntimeKeys.manifest("service-1"))
                .isEqualTo("security:runtime:v1:service:service-1:manifest");
        assertThat(RedisSecurityRuntimeKeys.clients("service-1"))
                .isEqualTo("security:runtime:v1:service:service-1:clients");
        assertThat(RedisSecurityRuntimeKeys.authConfigs("service-1"))
                .isEqualTo("security:runtime:v1:service:service-1:auth-configs");
        assertThat(RedisSecurityRuntimeKeys.permissions("service-1"))
                .isEqualTo("security:runtime:v1:service:service-1:permissions");
        assertThat(RedisSecurityRuntimeKeys.eventsChannel("service-1"))
                .isEqualTo("security:runtime:v1:service:service-1:events");
    }
}
