package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ClientRuntimeDTO {
    private String clientId;
    private String clientKey;
    private String name;
    private String status;
    private Boolean enabled;
    private Boolean active;
    private String revokedAt;
    private String updatedAt;
    private Long version;
}
