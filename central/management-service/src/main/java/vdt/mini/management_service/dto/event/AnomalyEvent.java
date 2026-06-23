package vdt.mini.management_service.dto.event;

import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnomalyEvent(String anomalyId,
                           String incidentId,
                           Instant timestamp,
                           String sourceType,
                           AnomalyType anomalyType,
                           String anomalyLevel,
                           String traceId,
                           String correlationId,
                           String serviceId,
                           String serviceName,
                           String endpointId,
                           String endpointName,
                           String flowType,
                           String direction,
                           AnomalyDecision decision,
                           int riskScore,
                           RuleConfidence confidence,
                           List<String> matchedRules,
                           List<String> detectedFeatures,
                           Map<String, Object> featureSnapshot,
                           String ruleSetVersion,
                           String logBaselineVersion,
                           String behaviorBaselineVersion,
                           Instant windowStart,
                           Instant windowEnd,
                           int windowSampleCount,
                           Instant firstSeenAt,
                           Instant lastSeenAt,
                           int matchedCount,
                           Instant createdAt) {
}
