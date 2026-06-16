package vdt.mini.management_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.ApplyGlobalToEndpointsRequest;
import vdt.mini.management_service.dto.request.ApplyGlobalToServicesRequest;
import vdt.mini.management_service.dto.request.ApplyServiceTemplateToEndpointsRequest;
import vdt.mini.management_service.dto.request.SettingTemplateUpdateRequest;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.dto.response.BatchApplyResponse;
import vdt.mini.management_service.dto.response.SettingTemplateResponse;
import vdt.mini.management_service.service.SettingTemplateBatchService;
import vdt.mini.management_service.service.SettingTemplateService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/central/api/configs")
public class SettingTemplateController {
    private final SettingTemplateService settingTemplateService;
    private final SettingTemplateBatchService settingTemplateBatchService;

    @GetMapping("/setting-templates/global")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<SettingTemplateResponse> getGlobalTemplate() {
        return ApiSuccessResponse.<SettingTemplateResponse>builder().status(200).message("OK").data(settingTemplateService.toResponse(settingTemplateService.getGlobalTemplate())).build();
    }

    @PutMapping("/setting-templates/global")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<SettingTemplateResponse> updateGlobalTemplate(@RequestBody SettingTemplateUpdateRequest request) {
        return ApiSuccessResponse.<SettingTemplateResponse>builder().status(200).message("Global template updated. Existing services/endpoints unchanged.").data(settingTemplateService.toResponse(settingTemplateService.updateGlobalTemplate(request))).build();
    }

    @PostMapping("/setting-templates/global/apply-to-services")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<BatchApplyResponse> applyGlobalToServices(@RequestBody(required = false) ApplyGlobalToServicesRequest request) {
        return ApiSuccessResponse.<BatchApplyResponse>builder().status(200).message("OK").data(settingTemplateBatchService.applyGlobalToServices(request)).build();
    }

    @PostMapping("/setting-templates/global/apply-to-endpoints")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<BatchApplyResponse> applyGlobalToEndpoints(@RequestBody(required = false) ApplyGlobalToEndpointsRequest request) {
        return ApiSuccessResponse.<BatchApplyResponse>builder().status(200).message("OK").data(settingTemplateBatchService.applyGlobalToEndpoints(request)).build();
    }

    @GetMapping("/services/{serviceId}/setting-template")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<SettingTemplateResponse> getServiceTemplate(@PathVariable String serviceId) {
        return ApiSuccessResponse.<SettingTemplateResponse>builder().status(200).message("OK").data(settingTemplateService.toResponse(settingTemplateService.getServiceTemplate(serviceId))).build();
    }

    @PutMapping("/services/{serviceId}/setting-template")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<SettingTemplateResponse> updateServiceTemplate(@PathVariable String serviceId, @RequestBody SettingTemplateUpdateRequest request) {
        return ApiSuccessResponse.<SettingTemplateResponse>builder().status(200).message("Service template updated. Existing endpoints unchanged.").data(settingTemplateService.toResponse(settingTemplateService.updateServiceTemplate(serviceId, request))).build();
    }

    @PostMapping("/services/{serviceId}/setting-template/apply-to-endpoints")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<BatchApplyResponse> applyServiceTemplateToEndpoints(@PathVariable String serviceId, @RequestBody(required = false) ApplyServiceTemplateToEndpointsRequest request) {
        return ApiSuccessResponse.<BatchApplyResponse>builder().status(200).message("OK").data(settingTemplateBatchService.applyServiceTemplateToEndpoints(serviceId, request)).build();
    }
}
