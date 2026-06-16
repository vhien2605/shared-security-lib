package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ServiceDetailResponse {
    private String id;
    private String name;
    private String description;
    private String baseUrl;
    private ServiceStatus status;
    private int inboundCount;
    private int outboundCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
