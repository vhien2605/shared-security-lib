package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnomalySearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sort = "timestamp";
    private String direction = "DESC";
    private String sortDirection = "DESC";
    private String from;
    private String to;
    private String serviceId;
    private String endpointId;
    private String anomalyType;
    private String anomalyLevel;
    private String decision;
    private String sourceType;
    private String flowType;
    private String eventDirection;
    private String incidentId;
    private String traceId;
    private Integer minRiskScore;
    private Integer maxRiskScore;
}
