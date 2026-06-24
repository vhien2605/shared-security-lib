package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.RiskScoreResult;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalySeverityResolverTest {
    @Test
    void resolve_sourceSeverityAlone_shouldRemainLow() {
        AnomalySeverityResolver resolver = new AnomalySeverityResolver(AnomalyTestFixtures.properties());

        String severity = resolver.resolve(AnomalyDecision.NORMAL, new RiskScoreResult(20, 3, Map.of()), RuleConfidence.LOW, null, List.of());

        assertThat(severity).isEqualTo("LOW");
    }
}
