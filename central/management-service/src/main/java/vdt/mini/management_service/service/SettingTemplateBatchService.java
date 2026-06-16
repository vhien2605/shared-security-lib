package vdt.mini.management_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.ApplyGlobalToEndpointsRequest;
import vdt.mini.management_service.dto.request.ApplyGlobalToServicesRequest;
import vdt.mini.management_service.dto.request.ApplyServiceTemplateToEndpointsRequest;
import vdt.mini.management_service.dto.response.BatchApplyResponse;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SettingTemplate;
import vdt.mini.management_service.repository.AlertConfigRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.repository.SettingTemplateRepository;
import vdt.mini.management_service.util.enums.EndpointType;
import vdt.mini.management_service.util.enums.SettingTemplateLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SettingTemplateBatchService {
    private final SettingTemplateService settingTemplateService;
    private final SettingTemplateRepository settingTemplateRepository;
    private final ServiceRepository serviceRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final SettingsValidationService settingsValidationService;
    private final RedisSettingsSyncService redisSettingsSyncService;

    @Transactional
    public BatchApplyResponse applyGlobalToServices(ApplyGlobalToServicesRequest request) {
        SettingTemplate global = settingTemplateService.getGlobalTemplate();
        if (request != null && request.getExpectedTemplateVersion() != null && !request.getExpectedTemplateVersion().equals(global.getVersion())) {
            throw new vdt.mini.management_service.exception.AppException(vdt.mini.management_service.util.enums.ErrorCode.SETTING_TEMPLATE_VERSION_CONFLICT);
        }
        List<String> serviceIds = request == null ? null : request.getServiceIds();
        List<vdt.mini.management_service.entity.SecureService> services = (serviceIds == null || serviceIds.isEmpty()) ? serviceRepository.findAll(Sort.by(Sort.Direction.ASC, "id")) : serviceRepository.findByIdInOrderByIdAsc(serviceIds);
        int affected = 0;
        for (vdt.mini.management_service.entity.SecureService service : services) {
            SettingTemplate serviceTemplate = settingTemplateService.ensureServiceTemplateExists(service.getId());
            settingTemplateService.copyTemplateValues(global, serviceTemplate);
            settingTemplateRepository.save(serviceTemplate);
            affected++;
        }
        return BatchApplyResponse.builder().target("GLOBAL_TO_SERVICES").affectedServices(affected).affectedInboundEndpoints(0).affectedOutboundEndpoints(0).skipped(0).message("Applied global template to service templates").build();
    }

    @Transactional
    public BatchApplyResponse applyGlobalToEndpoints(ApplyGlobalToEndpointsRequest request) {
        SettingTemplate global = settingTemplateService.getGlobalTemplate();
        if (request != null && request.getExpectedTemplateVersion() != null && !request.getExpectedTemplateVersion().equals(global.getVersion())) {
            throw new vdt.mini.management_service.exception.AppException(vdt.mini.management_service.util.enums.ErrorCode.SETTING_TEMPLATE_VERSION_CONFLICT);
        }
        List<EndpointType> endpointTypes = settingsValidationService.parseEndpointTypes(request == null ? null : request.getEndpointTypes());
        List<String> serviceIds = request == null ? null : request.getServiceIds();
        return applyTemplateToEndpoints(global, serviceIds, endpointTypes, null, "GLOBAL_TO_ENDPOINTS");
    }

    @Transactional
    public BatchApplyResponse applyServiceTemplateToEndpoints(String serviceId, ApplyServiceTemplateToEndpointsRequest request) {
        SettingTemplate template = settingTemplateService.getServiceTemplate(serviceId);
        if (request != null && request.getExpectedTemplateVersion() != null && !request.getExpectedTemplateVersion().equals(template.getVersion())) {
            throw new vdt.mini.management_service.exception.AppException(vdt.mini.management_service.util.enums.ErrorCode.SETTING_TEMPLATE_VERSION_CONFLICT);
        }
        List<EndpointType> endpointTypes = settingsValidationService.parseEndpointTypes(request == null ? null : request.getEndpointTypes());
        List<String> endpointIds = request == null ? null : request.getEndpointIds();
        return applyTemplateToEndpoints(template, List.of(serviceId), endpointTypes, endpointIds, "SERVICE_TO_ENDPOINTS");
    }

    private BatchApplyResponse applyTemplateToEndpoints(SettingTemplate template, List<String> serviceIds, List<EndpointType> endpointTypes, List<String> endpointIds, String target) {
        Set<String> affectedServiceIds = new HashSet<>();
        int inboundAffected = 0;
        int outboundAffected = 0;

        if (endpointTypes.contains(EndpointType.INBOUND)) {
            List<InboundEndpoint> inbounds = (serviceIds == null || serviceIds.isEmpty()) ? inboundEndpointRepository.findAll(Sort.by(Sort.Direction.ASC, "id")) : serviceIds.stream().flatMap(s -> inboundEndpointRepository.findBySecureServiceIdOrderByIdAsc(s).stream()).toList();
            for (InboundEndpoint inbound : inbounds) {
                if (endpointIds != null && !endpointIds.isEmpty() && !endpointIds.contains(inbound.getId())) continue;
                settingTemplateService.copyInboundDefaults(template, inbound);
                replaceAlert(inbound, template);
                inboundAffected++;
                affectedServiceIds.add(inbound.getSecureService().getId());
            }
            inboundEndpointRepository.saveAll(inbounds);
        }
        if (endpointTypes.contains(EndpointType.OUTBOUND)) {
            List<OutboundEndpoint> outbounds = (serviceIds == null || serviceIds.isEmpty()) ? outboundEndpointRepository.findAll(Sort.by(Sort.Direction.ASC, "id")) : serviceIds.stream().flatMap(s -> outboundEndpointRepository.findBySecureServiceIdOrderByIdAsc(s).stream()).toList();
            for (OutboundEndpoint outbound : outbounds) {
                if (endpointIds != null && !endpointIds.isEmpty() && !endpointIds.contains(outbound.getId())) continue;
                settingTemplateService.copyOutboundDefaults(template, outbound);
                replaceAlert(outbound, template);
                outboundAffected++;
                affectedServiceIds.add(outbound.getSecureService().getId());
            }
            outboundEndpointRepository.saveAll(outbounds);
        }

        if (!affectedServiceIds.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String serviceId : affectedServiceIds) {
                        redisSettingsSyncService.syncAllEndpointsOfService(serviceId);
                    }
                }
            });
        }

        return BatchApplyResponse.builder()
                .target(target)
                .affectedServices(affectedServiceIds.size())
                .affectedInboundEndpoints(inboundAffected)
                .affectedOutboundEndpoints(outboundAffected)
                .skipped(0)
                .message("Batch apply completed")
                .build();
    }

    private void replaceAlert(InboundEndpoint endpoint, SettingTemplate template) {
        AlertConfig alert = endpoint.getAlertConfig();
        if (alert == null) {
            endpoint.setAlertConfig(alertConfigRepository.save(settingTemplateService.createAlertConfigFromTemplate(template, endpoint.getName())));
            return;
        }
        alert.setSeverity(template.getAlertSeverity());
        alert.setThrottleMinutes(template.getAlertThrottleMinutes());
        alert.setChannels(new java.util.ArrayList<>(template.getAlertChannels()));
        alertConfigRepository.save(alert);
    }

    private void replaceAlert(OutboundEndpoint endpoint, SettingTemplate template) {
        AlertConfig alert = endpoint.getAlertConfig();
        if (alert == null) {
            endpoint.setAlertConfig(alertConfigRepository.save(settingTemplateService.createAlertConfigFromTemplate(template, endpoint.getName())));
            return;
        }
        alert.setSeverity(template.getAlertSeverity());
        alert.setThrottleMinutes(template.getAlertThrottleMinutes());
        alert.setChannels(new java.util.ArrayList<>(template.getAlertChannels()));
        alertConfigRepository.save(alert);
    }
}
