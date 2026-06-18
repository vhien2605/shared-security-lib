package vdt.mini.shared_lib.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityIdGeneratorTest {

    @Test
    void serviceId_shouldReturnStableLowercaseHex32AndTrimInputs() {
        String serviceId = SecurityIdGenerator.serviceId(" mini-project ", " user-service ");

        assertThat(serviceId).isEqualTo("cf4ba2ec2d5ad3e4bd4b657f57e9afaf");
        assertThat(serviceId).matches("[0-9a-f]{32}");
        assertThat(SecurityIdGenerator.serviceId("mini-project", "user-service")).isEqualTo(serviceId);
    }

    @Test
    void canonicalEndpointIdentity_shouldNormalizeRequiredFieldsAndPreserveDestinationCase() {
        String canonical = SecurityIdGenerator.canonicalEndpointIdentity(
                "service123", " inbound ", " http ", " get ", " /Users/Detail ", " group-A ");

        assertThat(canonical).isEqualTo("service123|INBOUND|HTTP|GET|/Users/Detail|group-A");
    }

    @Test
    void canonicalEndpointIdentity_shouldConvertNullOptionalFieldsToEmptyString() {
        String canonical = SecurityIdGenerator.canonicalEndpointIdentity(
                "service123", "OUTBOUND", "MQ", "PUB", null, null);

        assertThat(canonical).isEqualTo("service123|OUTBOUND|MQ|PUB||");
    }

    @Test
    void endpointId_shouldReturnStableLowercaseHex32() {
        String endpointId = SecurityIdGenerator.endpointId(
                "service123", "OUTBOUND", "MQ", "PUB", "Topic.CaseSensitive", "");

        assertThat(endpointId).matches("[0-9a-f]{32}");
        assertThat(endpointId).isEqualTo(SecurityIdGenerator.endpointId(
                "service123", " outbound ", " mq ", " pub ", " Topic.CaseSensitive ", null));
    }

    @Test
    void requiredInputs_shouldThrowWhenBlank() {
        assertThatThrownBy(() -> SecurityIdGenerator.serviceId(" ", "service"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityIdGenerator.serviceId("namespace", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityIdGenerator.endpointId(" ", "INBOUND", "HTTP", "GET", "/a", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityIdGenerator.endpointId("service", " ", "HTTP", "GET", "/a", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityIdGenerator.endpointId("service", "INBOUND", " ", "GET", "/a", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityIdGenerator.endpointId("service", "INBOUND", "HTTP", " ", "/a", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
