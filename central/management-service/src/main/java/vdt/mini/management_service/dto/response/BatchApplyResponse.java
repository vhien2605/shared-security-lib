package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchApplyResponse {
    private String target;
    private int affectedServices;
    private int affectedInboundEndpoints;
    private int affectedOutboundEndpoints;
    private int skipped;
    private String message;
}
