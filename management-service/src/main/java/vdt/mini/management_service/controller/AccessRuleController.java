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
import vdt.mini.management_service.dto.request.AccessRuleCreateRequest;
import vdt.mini.management_service.dto.request.AccessRuleUpdateRequest;
import vdt.mini.management_service.dto.response.AccessRuleDeleteResponse;
import vdt.mini.management_service.dto.response.AccessRuleResponse;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.service.AccessRuleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AccessRuleController {
    private final AccessRuleService accessRuleService;

    @PostMapping("/inbound-endpoints/{inboundEndpointId}/access-rules")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessRuleResponse> createAccessRule(
            @PathVariable String inboundEndpointId,
            @RequestBody AccessRuleCreateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessRuleResponse>builder()
                .status(200)
                .message("OK")
                .data(accessRuleService.createAccessRule(inboundEndpointId, request, authentication))
                .build();
    }

    @GetMapping("/inbound-endpoints/{inboundEndpointId}/access-rules")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Page<AccessRuleResponse>> listAccessRules(
            @PathVariable String inboundEndpointId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String valueType,
            @RequestParam(required = false) Boolean enable,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ApiSuccessResponse.<Page<AccessRuleResponse>>builder()
                .status(200)
                .message("OK")
                .data(accessRuleService.listAccessRules(inboundEndpointId, type, valueType, enable, keyword, pageable))
                .build();
    }

    @GetMapping("/access-rules")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Page<AccessRuleResponse>> listAllAccessRules(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String inboundEndpointId,
            @RequestParam(required = false) String endpointKeyword,
            @RequestParam(required = false) String valueType,
            @RequestParam(required = false) Boolean enable,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ApiSuccessResponse.<Page<AccessRuleResponse>>builder()
                .status(200)
                .message("OK")
                .data(accessRuleService.listAllAccessRules(type, inboundEndpointId, endpointKeyword, valueType, enable, keyword, pageable))
                .build();
    }

    @PatchMapping("/access-rules/{ruleId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessRuleResponse> updateAccessRule(
            @PathVariable String ruleId,
            @RequestBody AccessRuleUpdateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessRuleResponse>builder()
                .status(200)
                .message("OK")
                .data(accessRuleService.updateAccessRule(ruleId, request, authentication))
                .build();
    }

    @DeleteMapping("/access-rules/{ruleId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AccessRuleDeleteResponse> deleteAccessRule(
            @PathVariable String ruleId,
            Authentication authentication) {
        return ApiSuccessResponse.<AccessRuleDeleteResponse>builder()
                .status(200)
                .message("OK")
                .data(accessRuleService.deleteAccessRule(ruleId, authentication))
                .build();
    }
}
