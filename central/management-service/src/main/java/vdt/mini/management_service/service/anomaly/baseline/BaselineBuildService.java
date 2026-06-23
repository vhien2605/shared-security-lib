package vdt.mini.management_service.service.anomaly.baseline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.repository.ServiceRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BaselineBuildService {
    private static final Logger log = LoggerFactory.getLogger(BaselineBuildService.class);
    private final ElasticsearchSecurityLogBaselineSource baselineSource;
    private final LogBaselineBuilder logBaselineBuilder;
    private final BehaviorBaselineBuilder behaviorBaselineBuilder;
    private final BaselineActivationService activationService;
    private final ServiceRepository serviceRepository;

    public BaselineBuildService(ElasticsearchSecurityLogBaselineSource baselineSource,
                                LogBaselineBuilder logBaselineBuilder,
                                BehaviorBaselineBuilder behaviorBaselineBuilder,
                                BaselineActivationService activationService,
                                ServiceRepository serviceRepository) {
        this.baselineSource = baselineSource;
        this.logBaselineBuilder = logBaselineBuilder;
        this.behaviorBaselineBuilder = behaviorBaselineBuilder;
        this.activationService = activationService;
        this.serviceRepository = serviceRepository;
    }

    public void buildAllActiveGroups() {
        for (SecureService service : serviceRepository.findAll()) {
            try {
                buildForService(service.getId());
            } catch (RuntimeException exception) {
                log.warn("Baseline build failed for serviceId={}", service.getId(), exception);
            }
        }
    }

    public void buildForService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        List<SecurityLogEventMessage> events = baselineSource.loadRecentLogsForService(serviceId);
        if (events.isEmpty()) {
            log.info("No security logs found for baseline build: serviceId={}", serviceId);
            return;
        }
        Map<AnomalyGroupKey, BehaviorBaselineSnapshot> behaviors = behaviorBaselineBuilder.build(events).stream()
                .collect(Collectors.toMap(BehaviorBaselineSnapshot::groupKey, behavior -> behavior));
        for (LogBaselineSnapshot logBaseline : logBaselineBuilder.build(events)) {
            BehaviorBaselineSnapshot behaviorBaseline = behaviors.get(logBaseline.groupKey());
            activationService.activate(logBaseline, behaviorBaseline);
        }
    }
}
