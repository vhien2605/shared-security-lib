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
public class RuntimeChangePayloadDTO {
    private ClientRuntimeDTO client;
    private List<AuthConfigRuntimeDTO> authConfigs;
    private List<PermissionRuntimeDTO> permissions;
    private List<RuntimeTombstoneDTO> tombstones;
}
