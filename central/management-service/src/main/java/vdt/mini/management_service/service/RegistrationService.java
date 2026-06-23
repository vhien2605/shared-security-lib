package vdt.mini.management_service.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.event.InboundEndpointDTO;
import vdt.mini.management_service.dto.event.OutboundEndpointDTO;
import vdt.mini.management_service.dto.event.ServiceRegistrationEvent;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.repository.AlertConfigRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.service.anomaly.baseline.BaselineBuildService;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.EndpointStatus;
import vdt.mini.management_service.util.enums.RollbackStrategy;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final ServiceRepository serviceRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final RedisSettingsSyncService redisSettingsSyncService;
    private final SettingTemplateService settingTemplateService;
    private final BaselineBuildService baselineBuildService;

    public RegistrationService(ServiceRepository serviceRepository,
                               InboundEndpointRepository inboundEndpointRepository,
                               OutboundEndpointRepository outboundEndpointRepository,
                                AlertConfigRepository alertConfigRepository,
                                RedisSettingsSyncService redisSettingsSyncService,
                                SettingTemplateService settingTemplateService,
                                BaselineBuildService baselineBuildService) {
        this.serviceRepository = serviceRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.outboundEndpointRepository = outboundEndpointRepository;
        this.alertConfigRepository = alertConfigRepository;
        this.redisSettingsSyncService = redisSettingsSyncService;
        this.settingTemplateService = settingTemplateService;
        this.baselineBuildService = baselineBuildService;
    }

    @Transactional
    public void processRegistration(ServiceRegistrationEvent event) {
        log.info("TX={}", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("THREAD={}, SERVICE_ID={}",
                Thread.currentThread().getName(),
                event.getServiceId());

        // =========================
        // 1. UPSERT SERVICE - TRÁNH MERGE CHO ENTITY ĐÃ MANAGED
        // =========================
        SecureService service = serviceRepository.findById(event.getServiceId())
                .orElse(null);

        boolean serviceCreated = false;
        if (service != null) {
            service.setName(event.getServiceName());
            service.setBaseUrl(event.getBaseUrl());
            service.setDescription(event.getDescription());
        } else {
            service = new SecureService();
            service.setId(event.getServiceId());
            service.setStatus(ServiceStatus.ACTIVE);
            service.setName(event.getServiceName());
            service.setBaseUrl(event.getBaseUrl());
            service.setDescription(event.getDescription());
            service = serviceRepository.save(service);
            serviceCreated = true;
        }

        var serviceTemplate = serviceCreated
                ? settingTemplateService.createServiceTemplateFromGlobal(service)
                : settingTemplateService.ensureServiceTemplateExists(service.getId());


        // =========================
        // 2. INBOUNDS (BATCH SAFE)
        // =========================
        List<InboundEndpoint> inbounds = new ArrayList<>();
        Set<String> inboundIdsFromEvent = new HashSet<>();

        List<InboundEndpointDTO> inboundEvents = event.getInbounds() != null ? event.getInbounds() : List.of();
        for (InboundEndpointDTO dto : inboundEvents) {
            if (dto.getEndpointId() == null) {
                continue;
            }
            inboundIdsFromEvent.add(dto.getEndpointId());
            boolean shouldEnable = dto.getEnabled() == null || dto.getEnabled();

            final boolean[] isNewEndpoint = {false};
            InboundEndpoint ep = inboundEndpointRepository.findAnyByIdWithAlert(dto.getEndpointId())
                    .orElseGet(() -> {
                        InboundEndpoint created = new InboundEndpoint();
                        created.setId(dto.getEndpointId());
                        isNewEndpoint[0] = true;
                        return created;
                    });

            ep.setSecureService(service);
            ep.setName(dto.getName());
            ep.setPath(dto.getPath());
            ep.setTopic(dto.getTopic());
            ep.setMethod(dto.getMethod() != null
                    ? EndpointMethod.valueOf(dto.getMethod())
                    : null);
            ep.setProtocol(dto.getProtocol() != null
                    ? EndpointProtocol.valueOf(dto.getProtocol())
                    : null);
            ep.setEnabled(shouldEnable);
            if (isNewEndpoint[0] || ep.getStatus() == null) {
                ep.setStatus(EndpointStatus.ACTIVE);
            }

            if (isNewEndpoint[0] && shouldEnable) {
                settingTemplateService.copyInboundDefaults(serviceTemplate, ep);
                AlertConfig alertConfig = settingTemplateService.createAlertConfigFromTemplate(serviceTemplate, dto.getName());
                ep.setAlertConfig(alertConfigRepository.save(alertConfig));
            }

            inbounds.add(ep);
        }
        inboundEndpointRepository.saveAll(inbounds);
        // =========================
        // 3. OUTBOUNDS (BATCH SAFE)
        // =========================
        List<OutboundEndpoint> outbounds = new ArrayList<>();
        Set<String> outboundIdsFromEvent = new HashSet<>();

        List<OutboundEndpointDTO> outboundEvents = event.getOutbounds() != null ? event.getOutbounds() : List.of();
        for (OutboundEndpointDTO dto : outboundEvents) {
            if (dto.getEndpointId() == null) {
                continue;
            }
            outboundIdsFromEvent.add(dto.getEndpointId());
            boolean shouldEnable = dto.getEnabled() == null || dto.getEnabled();

            final boolean[] isNewEndpoint = {false};
            OutboundEndpoint ep = outboundEndpointRepository.findAnyByIdWithAlert(dto.getEndpointId())
                    .orElseGet(() -> {
                        OutboundEndpoint created = new OutboundEndpoint();
                        created.setId(dto.getEndpointId());
                        isNewEndpoint[0] = true;
                        return created;
                    });

            ep.setSecureService(service);
            ep.setName(dto.getName());
            ep.setTargetUrl(dto.getTargetUrl());
            ep.setTopic(dto.getTopic());
            ep.setMethod(dto.getMethod() != null
                    ? EndpointMethod.valueOf(dto.getMethod())
                    : null);
            ep.setProtocol(dto.getProtocol() != null
                    ? EndpointProtocol.valueOf(dto.getProtocol())
                    : null);
            ep.setEnabled(shouldEnable);
            if (isNewEndpoint[0] || ep.getStatus() == null) {
                ep.setStatus(EndpointStatus.ACTIVE);
            }

            if (isNewEndpoint[0] && shouldEnable) {
                settingTemplateService.copyOutboundDefaults(serviceTemplate, ep);
                if (ep.getRollbackStrategy() == null) {
                    ep.setRollbackStrategy(RollbackStrategy.IGNORE);
                }
                AlertConfig alertConfig = settingTemplateService.createAlertConfigFromTemplate(serviceTemplate, dto.getName());
                ep.setAlertConfig(alertConfigRepository.save(alertConfig));
            }

            outbounds.add(ep);
        }
        outboundEndpointRepository.saveAll(outbounds);

        List<InboundEndpoint> staleInbounds = inboundEndpointRepository.findAllBySecureServiceId(service.getId())
                .stream()
                .filter(ep -> !inboundIdsFromEvent.contains(ep.getId()))
                .filter(ep -> Boolean.TRUE.equals(ep.getEnabled()))
                .peek(ep -> ep.setEnabled(false))
                .toList();
        if (!staleInbounds.isEmpty()) {
            inboundEndpointRepository.saveAll(staleInbounds);
        }

        List<OutboundEndpoint> staleOutbounds = outboundEndpointRepository.findAllBySecureServiceId(service.getId())
                .stream()
                .filter(ep -> !outboundIdsFromEvent.contains(ep.getId()))
                .filter(ep -> Boolean.TRUE.equals(ep.getEnabled()))
                .peek(ep -> ep.setEnabled(false))
                .toList();
        if (!staleOutbounds.isEmpty()) {
            outboundEndpointRepository.saveAll(staleOutbounds);
        }

        log.info("Processed registration: service={}, inbounds={}, outbounds={}",
                event.getServiceName(),
                inbounds.size(),
                outbounds.size());

        // Đăng ký afterCommit hook để sync Redis sau khi transaction thành công
        String serviceId = event.getServiceId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisSettingsSyncService.syncAllEndpointsOfService(serviceId);
                        try {
                            baselineBuildService.buildForService(serviceId);
                        } catch (RuntimeException exception) {
                            log.warn("Baseline build after registration failed for serviceId={}", serviceId, exception);
                        }
                    }
                }
        );
        log.info("Registered afterCommit hook for Redis sync: serviceId={}", serviceId);
    }
}
