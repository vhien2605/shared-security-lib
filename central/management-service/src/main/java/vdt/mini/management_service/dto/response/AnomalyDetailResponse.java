package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AnomalyDetailResponse {
    private String anomalyId;
    private String incidentId;
    private String timestamp;
    private String sourceType;
    private String anomalyType;
    private String anomalyLevel;
    private String status;
    private String traceId;
    private String correlationId;
    private String serviceId;
    private String serviceName;
    private String endpointId;
    private String endpointName;
    private String flowType;
    private String direction;
    private String decision;
    private Integer riskScore;
    private Integer maxRiskScore;
    private String maxSeverity;
    private String confidence;
    private List<String> matchedRules;
    private List<String> detectedFeatures;
    private Map<String, Object> featureSnapshot;
    private Map<String, Object> latestFeatureSnapshot;
    private Map<String, Object> effectiveFeatureSnapshot;
    private String ruleSetVersion;
    private String logBaselineVersion;
    private String behaviorBaselineVersion;
    private String windowStart;
    private String windowEnd;
    private Integer windowSampleCount;
    private String firstSeenAt;
    private String lastSeenAt;
    private Integer matchedCount;
    private String createdAt;
}
