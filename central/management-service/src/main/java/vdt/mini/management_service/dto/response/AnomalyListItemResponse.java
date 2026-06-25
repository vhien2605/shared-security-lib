package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyListItemResponse {
    private String anomalyId;
    private String incidentId;
    private String timestamp;
    private String sourceType;
    private String anomalyType;
    private String anomalyLevel;
    private String serviceId;
    private String serviceName;
    private String endpointId;
    private String endpointName;
    private String flowType;
    private String direction;
    private String decision;
    private Integer riskScore;
    private Integer maxRiskScore;
    private String status;
    private Integer matchedCount;
    private String traceId;
    private String correlationId;
    private String lastSeenAt;
}
