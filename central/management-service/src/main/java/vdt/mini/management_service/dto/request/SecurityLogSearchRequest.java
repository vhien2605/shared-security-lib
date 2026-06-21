package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SecurityLogSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortDirection = "DESC";
    private String from;
    private String to;
    private String serviceId;
    private String serviceName;
    private String endpointId;
    private String endpointName;
    private String flowType;
    private String direction;
    private String protocol;
    private String method;
    private String status;
    private String resultCode;
    private String errorCode;
    private String clientId;
    private String clientKey;
    private String traceId;
    private String correlationId;
    private String alertSeverity;
    private String target;
}
