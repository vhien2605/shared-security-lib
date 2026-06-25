package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.util.enums.AlertSeverity;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSeverityPolicyTest {
    private final AlertSeverityPolicy policy = new AlertSeverityPolicy();

    @Test
    void allows_shouldMapConfiguredThresholdToAnomalySeverity() {
        assertThat(policy.allows(AlertSeverity.INFO, "LOW")).isTrue();
        assertThat(policy.allows(AlertSeverity.WARNING, "LOW")).isFalse();
        assertThat(policy.allows(AlertSeverity.WARNING, "MEDIUM")).isTrue();
        assertThat(policy.allows(AlertSeverity.CRITICAL, "HIGH")).isFalse();
        assertThat(policy.allows(AlertSeverity.CRITICAL, "CRITICAL")).isTrue();
    }
}
