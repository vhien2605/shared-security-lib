package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServicePermissionsSnapshotDTO {
    private String serviceId;
    private Long version;
    private List<PermissionRuntimeDTO> permissions;
}
