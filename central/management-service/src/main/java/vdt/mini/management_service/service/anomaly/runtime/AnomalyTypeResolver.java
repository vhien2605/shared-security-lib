package vdt.mini.management_service.service.anomaly.runtime;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.RuleMatch;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class AnomalyTypeResolver {
    private static final Set<AnomalyType> COMPOSITE_TYPES = Set.of(AnomalyType.UPSTREAM_DEGRADATION, AnomalyType.AUTHENTICATION_ATTACK_PATTERN, AnomalyType.DEPENDENCY_INSTABILITY);
    private static final Set<AnomalyType> BEHAVIORAL_TYPES = Set.of(AnomalyType.FAILURE_SPIKE, AnomalyType.DENIED_SPIKE, AnomalyType.RETRY_SPIKE,
            AnomalyType.TRAFFIC_SPIKE, AnomalyType.TRAFFIC_DROP, AnomalyType.LATENCY_DRIFT, AnomalyType.REQUEST_SIZE_DRIFT,
            AnomalyType.RESPONSE_SIZE_DRIFT, AnomalyType.MESSAGE_SIZE_DRIFT, AnomalyType.SOURCE_IP_SPIKE,
            AnomalyType.CLIENT_DIVERSITY_SPIKE, AnomalyType.ERROR_DISTRIBUTION_DRIFT);

    public AnomalyType resolve(List<RuleMatch> matches) {
        if (matches == null || matches.isEmpty()) return null;
        return matches.stream().min(Comparator.<RuleMatch>comparingInt(this::categoryRank)
                .thenComparing(Comparator.<RuleMatch>comparingInt(RuleMatch::riskPoints).reversed())
                .thenComparing(Comparator.<RuleMatch>comparingInt(match -> confidenceRank(match.confidence())).reversed())
                .thenComparingInt(match -> typePriority(match.anomalyType()))
                .thenComparing(RuleMatch::ruleId)).orElseThrow().anomalyType();
    }

    private int categoryRank(RuleMatch match) {
        if (COMPOSITE_TYPES.contains(match.anomalyType())) return 0;
        if (BEHAVIORAL_TYPES.contains(match.anomalyType()) && match.confidence() == RuleConfidence.HIGH) return 1;
        if (!BEHAVIORAL_TYPES.contains(match.anomalyType()) && match.confidence() == RuleConfidence.HIGH) return 2;
        return 3;
    }

    private int confidenceRank(RuleConfidence confidence) {
        if (confidence == RuleConfidence.HIGH) return 3;
        if (confidence == RuleConfidence.MEDIUM) return 2;
        return 1;
    }

    private int typePriority(AnomalyType type) {
        if (type == AnomalyType.UPSTREAM_DEGRADATION) return 0;
        if (type == AnomalyType.AUTHENTICATION_ATTACK_PATTERN) return 1;
        if (type == AnomalyType.DEPENDENCY_INSTABILITY) return 2;
        return 10;
    }
}
