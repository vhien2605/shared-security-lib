package vdt.mini.management_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.AccessPermissionCreateRequest;
import vdt.mini.management_service.dto.request.AccessPermissionUpdateRequest;
import vdt.mini.management_service.dto.response.AccessPermissionDeleteResponse;
import vdt.mini.management_service.dto.response.AccessPermissionResponse;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.service.AccessPermissionService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/access-permissions")
public class AccessPermissionController {
    private final AccessPermissionService accessPermissionService;

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessPermissionResponse> createPermission(
            @RequestBody AccessPermissionCreateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessPermissionResponse>builder()
                .status(200)
                .message("OK")
                .data(accessPermissionService.createPermission(request, authentication))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Page<AccessPermissionResponse>> listPermissions(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String inboundEndpointId,
            @RequestParam(required = false) Boolean enable,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ApiSuccessResponse.<Page<AccessPermissionResponse>>builder()
                .status(200)
                .message("OK")
                .data(accessPermissionService.listPermissions(clientId, inboundEndpointId, enable, keyword, pageable))
                .build();
    }

    @GetMapping("/{permissionId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessPermissionResponse> getPermission(@PathVariable String permissionId) {
        return ApiSuccessResponse.<AccessPermissionResponse>builder()
                .status(200)
                .message("OK")
                .data(accessPermissionService.getPermission(permissionId))
                .build();
    }

    @PatchMapping("/{permissionId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessPermissionResponse> updatePermission(
            @PathVariable String permissionId,
            @RequestBody AccessPermissionUpdateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessPermissionResponse>builder()
                .status(200)
                .message("OK")
                .data(accessPermissionService.updatePermission(permissionId, request, authentication))
                .build();
    }

    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessPermissionDeleteResponse> deletePermission(
            @PathVariable String permissionId,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessPermissionDeleteResponse>builder()
                .status(200)
                .message("OK")
                .data(accessPermissionService.deletePermission(permissionId, authentication))
                .build();
    }
}
