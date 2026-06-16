package vdt.mini.management_service.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.EndpointStatusPatchRequest;
import vdt.mini.management_service.dto.request.InboundSettingsPatchRequest;
import vdt.mini.management_service.dto.request.OutboundSettingsPatchRequest;
import vdt.mini.management_service.dto.request.ServiceStatusPatchRequest;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.dto.response.InboundEndpointResponse;
import vdt.mini.management_service.dto.response.OutboundEndpointResponse;
import vdt.mini.management_service.dto.response.ServiceDetailResponse;
import vdt.mini.management_service.dto.response.ServiceListResponse;
import vdt.mini.management_service.service.AvailabilityManagementService;
import vdt.mini.management_service.service.ConfigQueryService;
import vdt.mini.management_service.service.EndpointSettingsService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/central/api/configs")
public class ConfigManagementController {

    private final ConfigQueryService configQueryService;
    private final EndpointSettingsService endpointSettingsService;
    private final AvailabilityManagementService availabilityManagementService;

    @GetMapping("/test")
    public String test(Authentication authentication) {
        return "nice";
    }

    // ==================== SERVICES ====================

    @GetMapping("/services")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Page<ServiceListResponse>> getServices(Pageable pageable) {
        Page<ServiceListResponse> data = configQueryService.getServices(pageable);
        return ApiSuccessResponse.<Page<ServiceListResponse>>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/services/{serviceId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<ServiceDetailResponse> getServiceDetail(@PathVariable String serviceId) {
        ServiceDetailResponse data = configQueryService.getServiceDetail(serviceId);
        return ApiSuccessResponse.<ServiceDetailResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/services/search")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<List<ServiceListResponse>> searchServices(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer size) {
        List<ServiceListResponse> data = configQueryService.searchServicesByName(name, size);
        return ApiSuccessResponse.<List<ServiceListResponse>>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @PatchMapping("/services/{serviceId}/status")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<ServiceDetailResponse> updateServiceStatus(
            @PathVariable String serviceId,
            @RequestBody ServiceStatusPatchRequest request) {
        availabilityManagementService.updateServiceStatus(serviceId, request != null ? request.getStatus() : null);
        ServiceDetailResponse data = configQueryService.getServiceDetail(serviceId);
        return ApiSuccessResponse.<ServiceDetailResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/services/{serviceId}/inbounds")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<List<InboundEndpointResponse>> getInboundsByService(@PathVariable String serviceId) {
        List<InboundEndpointResponse> data = configQueryService.getInboundsByService(serviceId);
        return ApiSuccessResponse.<List<InboundEndpointResponse>>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/services/{serviceId}/outbounds")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<List<OutboundEndpointResponse>> getOutboundsByService(@PathVariable String serviceId) {
        List<OutboundEndpointResponse> data = configQueryService.getOutboundsByService(serviceId);
        return ApiSuccessResponse.<List<OutboundEndpointResponse>>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }
    
    @GetMapping("/inbounds/{endpointId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<InboundEndpointResponse> getInboundDetail(@PathVariable String endpointId) {
        InboundEndpointResponse data = configQueryService.getInboundDetail(endpointId);
        return ApiSuccessResponse.<InboundEndpointResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @PatchMapping("/inbounds/{endpointId}/settings")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Void> updateInboundSettings(
            @PathVariable String endpointId,
            @RequestBody InboundSettingsPatchRequest request) {
        endpointSettingsService.updateInboundSettings(endpointId, request);
        return ApiSuccessResponse.<Void>builder()
                .status(200)
                .message("OK")
                .build();
    }

    @PatchMapping("/inbounds/{endpointId}/status")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<InboundEndpointResponse> updateInboundStatus(
            @PathVariable String endpointId,
            @RequestBody EndpointStatusPatchRequest request) {
        availabilityManagementService.updateInboundStatus(endpointId, request != null ? request.getStatus() : null);
        InboundEndpointResponse data = configQueryService.getInboundDetail(endpointId);
        return ApiSuccessResponse.<InboundEndpointResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    // ==================== OUTBOUND ENDPOINTS ====================

    @GetMapping("/outbounds/{endpointId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<OutboundEndpointResponse> getOutboundDetail(@PathVariable String endpointId) {
        OutboundEndpointResponse data = configQueryService.getOutboundDetail(endpointId);
        return ApiSuccessResponse.<OutboundEndpointResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }

    @PatchMapping("/outbounds/{endpointId}/settings")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Void> updateOutboundSettings(
            @PathVariable String endpointId,
            @RequestBody OutboundSettingsPatchRequest request) {
        endpointSettingsService.updateOutboundSettings(endpointId, request);
        return ApiSuccessResponse.<Void>builder()
                .status(200)
                .message("OK")
                .build();
    }

    @PatchMapping("/outbounds/{endpointId}/status")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<OutboundEndpointResponse> updateOutboundStatus(
            @PathVariable String endpointId,
            @RequestBody EndpointStatusPatchRequest request) {
        availabilityManagementService.updateOutboundStatus(endpointId, request != null ? request.getStatus() : null);
        OutboundEndpointResponse data = configQueryService.getOutboundDetail(endpointId);
        return ApiSuccessResponse.<OutboundEndpointResponse>builder()
                .status(200)
                .message("OK")
                .data(data)
                .build();
    }
}
