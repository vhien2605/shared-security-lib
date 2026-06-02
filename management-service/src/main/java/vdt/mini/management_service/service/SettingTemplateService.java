package vdt.mini.management_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.dto.request.SettingTemplateUpdateRequest;
import vdt.mini.management_service.dto.response.SettingTemplateResponse;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.entity.SettingTemplate;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.SettingTemplateRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.AlertSeverity;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.util.enums.RollbackStrategy;
import vdt.mini.management_service.util.enums.SettingTemplateLevel;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingTemplateService {
    private final SettingTemplateRepository settingTemplateRepository;
    private final ServiceRepository serviceRepository;
    private final SettingsValidationService settingsValidationService;

    @Transactional(readOnly = true)
    public SettingTemplate getGlobalTemplate() {
        return settingTemplateRepository.findFirstByLevelOrderByIdAsc(SettingTemplateLevel.GLOBAL)
                .orElseThrow(() -> new AppException(ErrorCode.GLOBAL_TEMPLATE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SettingTemplate getServiceTemplate(String serviceId) {
        ensureServiceTemplateExists(serviceId);
        return settingTemplateRepository.findByLevelAndSecureServiceId(SettingTemplateLevel.SERVICE, serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_TEMPLATE_NOT_FOUND));
    }

    @Transactional
    public SettingTemplate updateGlobalTemplate(SettingTemplateUpdateRequest request) {
        settingsValidationService.validateTemplateUpdate(request);
        SettingTemplate template = getGlobalTemplate();
        checkVersion(template, request.getExpectedVersion());
        applyRequest(template, request);
        return settingTemplateRepository.save(template);
    }

    @Transactional
    public SettingTemplate updateServiceTemplate(String serviceId, SettingTemplateUpdateRequest request) {
        settingsValidationService.validateTemplateUpdate(request);
        SettingTemplate template = getServiceTemplate(serviceId);
        checkVersion(template, request.getExpectedVersion());
        applyRequest(template, request);
        return settingTemplateRepository.save(template);
    }

    @Transactional
    public SettingTemplate createServiceTemplateFromGlobal(SecureService service) {
        SettingTemplate existed = settingTemplateRepository.findByLevelAndSecureServiceId(SettingTemplateLevel.SERVICE, service.getId()).orElse(null);
        if (existed != null) {
            return existed;
        }
        SettingTemplate global = getGlobalTemplate();
        SettingTemplate created = new SettingTemplate();
        created.setId(UUID.randomUUID().toString());
        created.setLevel(SettingTemplateLevel.SERVICE);
        created.setSecureService(service);
        copyTemplateValues(global, created);
        return settingTemplateRepository.save(created);
    }

    @Transactional
    public void ensureServiceTemplatesForExistingServices() {
        for (SecureService service : serviceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            ensureServiceTemplateExists(service.getId());
        }
    }

    @Transactional
    public SettingTemplate ensureServiceTemplateExists(String serviceId) {
        return settingTemplateRepository.findByLevelAndSecureServiceId(SettingTemplateLevel.SERVICE, serviceId)
                .orElseGet(() -> createServiceTemplateFromGlobal(serviceRepository.findById(serviceId)
                        .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND))));
    }

    public void copyInboundDefaults(SettingTemplate template, InboundEndpoint endpoint) {
        endpoint.setRateLimit(template.getInboundRateLimit());
        endpoint.setRateLimitWindowSeconds(template.getInboundRateLimitWindowSeconds());
        endpoint.setTimeoutMs(template.getInboundTimeoutMs());
        endpoint.setRequestSizeLimitKb(template.getInboundRequestSizeLimitKb());
        endpoint.setResponseSizeLimitKb(template.getInboundResponseSizeLimitKb());
        endpoint.setResponseTimeThresholdMs(template.getInboundResponseTimeThresholdMs());
        endpoint.setLogRetentionDays(template.getInboundLogRetentionDays());
    }

    public void copyOutboundDefaults(SettingTemplate template, OutboundEndpoint endpoint) {
        endpoint.setTimeoutMs(template.getOutboundTimeoutMs());
        endpoint.setRetryCount(template.getOutboundRetryCount());
        endpoint.setRetryBackoffMs(template.getOutboundRetryBackoffMs());
        endpoint.setResponseTimeThresholdMs(template.getOutboundResponseTimeThresholdMs());
        endpoint.setLogRetentionDays(template.getOutboundLogRetentionDays());
        endpoint.setRollbackStrategy(template.getOutboundRollbackStrategy());
    }

    public AlertConfig createAlertConfigFromTemplate(SettingTemplate template, String endpointName) {
        AlertConfig config = new AlertConfig();
        config.setId(UUID.randomUUID().toString());
        config.setName(endpointName + "-alert");
        config.setSeverity(template.getAlertSeverity());
        config.setThrottleMinutes(template.getAlertThrottleMinutes());
        config.setChannels(new ArrayList<>(template.getAlertChannels()));
        return config;
    }

    public SettingTemplateResponse toResponse(SettingTemplate template) {
        return SettingTemplateResponse.builder()
                .id(template.getId())
                .level(template.getLevel().name())
                .serviceId(template.getSecureService() == null ? null : template.getSecureService().getId())
                .version(template.getVersion())
                .inboundRateLimit(template.getInboundRateLimit())
                .inboundRateLimitWindowSeconds(template.getInboundRateLimitWindowSeconds())
                .inboundTimeoutMs(template.getInboundTimeoutMs())
                .inboundRequestSizeLimitKb(template.getInboundRequestSizeLimitKb())
                .inboundResponseSizeLimitKb(template.getInboundResponseSizeLimitKb())
                .inboundResponseTimeThresholdMs(template.getInboundResponseTimeThresholdMs())
                .inboundLogRetentionDays(template.getInboundLogRetentionDays())
                .outboundTimeoutMs(template.getOutboundTimeoutMs())
                .outboundRetryCount(template.getOutboundRetryCount())
                .outboundRetryBackoffMs(template.getOutboundRetryBackoffMs())
                .outboundResponseTimeThresholdMs(template.getOutboundResponseTimeThresholdMs())
                .outboundLogRetentionDays(template.getOutboundLogRetentionDays())
                .outboundRollbackStrategy(template.getOutboundRollbackStrategy().name())
                .alertSeverity(template.getAlertSeverity().name())
                .alertThrottleMinutes(template.getAlertThrottleMinutes())
                .alertChannels(template.getAlertChannels())
                .build();
    }

    public void copyTemplateValues(SettingTemplate source, SettingTemplate target) {
        target.setInboundRateLimit(source.getInboundRateLimit());
        target.setInboundRateLimitWindowSeconds(source.getInboundRateLimitWindowSeconds());
        target.setInboundTimeoutMs(source.getInboundTimeoutMs());
        target.setInboundRequestSizeLimitKb(source.getInboundRequestSizeLimitKb());
        target.setInboundResponseSizeLimitKb(source.getInboundResponseSizeLimitKb());
        target.setInboundResponseTimeThresholdMs(source.getInboundResponseTimeThresholdMs());
        target.setInboundLogRetentionDays(source.getInboundLogRetentionDays());
        target.setOutboundTimeoutMs(source.getOutboundTimeoutMs());
        target.setOutboundRetryCount(source.getOutboundRetryCount());
        target.setOutboundRetryBackoffMs(source.getOutboundRetryBackoffMs());
        target.setOutboundResponseTimeThresholdMs(source.getOutboundResponseTimeThresholdMs());
        target.setOutboundLogRetentionDays(source.getOutboundLogRetentionDays());
        target.setOutboundRollbackStrategy(source.getOutboundRollbackStrategy());
        target.setAlertSeverity(source.getAlertSeverity());
        target.setAlertThrottleMinutes(source.getAlertThrottleMinutes());
        target.setAlertChannels(new ArrayList<>(source.getAlertChannels()));
    }

    private void applyRequest(SettingTemplate template, SettingTemplateUpdateRequest request) {
        template.setInboundRateLimit(request.getInboundRateLimit());
        template.setInboundRateLimitWindowSeconds(request.getInboundRateLimitWindowSeconds());
        template.setInboundTimeoutMs(request.getInboundTimeoutMs());
        template.setInboundRequestSizeLimitKb(request.getInboundRequestSizeLimitKb());
        template.setInboundResponseSizeLimitKb(request.getInboundResponseSizeLimitKb());
        template.setInboundResponseTimeThresholdMs(request.getInboundResponseTimeThresholdMs());
        template.setInboundLogRetentionDays(request.getInboundLogRetentionDays());
        template.setOutboundTimeoutMs(request.getOutboundTimeoutMs());
        template.setOutboundRetryCount(request.getOutboundRetryCount());
        template.setOutboundRetryBackoffMs(request.getOutboundRetryBackoffMs());
        template.setOutboundResponseTimeThresholdMs(request.getOutboundResponseTimeThresholdMs());
        template.setOutboundLogRetentionDays(request.getOutboundLogRetentionDays());
        template.setOutboundRollbackStrategy(RollbackStrategy.valueOf(request.getOutboundRollbackStrategy()));
        template.setAlertSeverity(AlertSeverity.valueOf(request.getAlertSeverity()));
        template.setAlertThrottleMinutes(request.getAlertThrottleMinutes());
        template.setAlertChannels(new ArrayList<>(request.getAlertChannels()));
    }

    private void checkVersion(SettingTemplate template, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(template.getVersion())) {
            throw new AppException(ErrorCode.SETTING_TEMPLATE_VERSION_CONFLICT);
        }
    }
}
