package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.RuleMatch;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyTypeResolverTest {
    @Test
    void resolve_compositeWinsOverBehavioralAndHistorical() {
        AnomalyType resolved = new AnomalyTypeResolver().resolve(List.of(
                new RuleMatch("BEHAVIOR_FAILURE_001", AnomalyType.FAILURE_SPIKE, 5, RuleConfidence.HIGH, List.of(), ""),
                new RuleMatch("COMPOSITE_UPSTREAM_001", AnomalyType.UPSTREAM_DEGRADATION, 4, RuleConfidence.HIGH, List.of(), "")));

        assertThat(resolved).isEqualTo(AnomalyType.UPSTREAM_DEGRADATION);
    }

    @Test
    void resolveAll_returnsDistinctTypesInPriorityOrder() {
        List<AnomalyType> resolved = new AnomalyTypeResolver().resolveAll(List.of(
                new RuleMatch("BEHAVIOR_RETRY_001", AnomalyType.RETRY_SPIKE, 5, RuleConfidence.HIGH, List.of(), ""),
                new RuleMatch("BEHAVIOR_FAILURE_001", AnomalyType.FAILURE_SPIKE, 5, RuleConfidence.HIGH, List.of(), ""),
                new RuleMatch("BEHAVIOR_FAILURE_001_DUP", AnomalyType.FAILURE_SPIKE, 5, RuleConfidence.HIGH, List.of(), ""),
                new RuleMatch("COMPOSITE_UPSTREAM_001", AnomalyType.UPSTREAM_DEGRADATION, 4, RuleConfidence.HIGH, List.of(), "")));

        assertThat(resolved).containsExactly(AnomalyType.UPSTREAM_DEGRADATION, AnomalyType.FAILURE_SPIKE, AnomalyType.RETRY_SPIKE);
    }
}
