package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AccessRuleCreateRequest {
    private String type;
    private String valueType;
    private String value;
    private Boolean temporary;
    private LocalDateTime expiresAt;
    private String reason;
}
