package vdt.mini.management_service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "security-anomalies-*", createIndex = false)
public class AnomalyDocument {
    @Id
    private String id;

    private String anomalyId;
    private String incidentId;
    private String timestamp;
    private String sourceType;
    private String anomalyType;
    private String anomalyLevel;
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
    private String ruleSetVersion;
    private String logBaselineVersion;
    private String behaviorBaselineVersion;
    private String windowStart;
    private String windowEnd;
    private Integer windowSampleCount;
    private String firstSeenAt;
    private String lastSeenAt;
    private Integer matchedCount;
    private String status;
    private String createdAt;
}
