package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.repository.SecurityBehaviorBaselineRepository;
import vdt.mini.management_service.repository.SecurityLogBaselineRepository;

@Service
public class BaselineActivationService {
    private final SecurityLogBaselineRepository logRepository;
    private final SecurityBehaviorBaselineRepository behaviorRepository;
    private final BaselineCache baselineCache;

    public BaselineActivationService(SecurityLogBaselineRepository logRepository,
                                     SecurityBehaviorBaselineRepository behaviorRepository,
                                     BaselineCache baselineCache) {
        this.logRepository = logRepository;
        this.behaviorRepository = behaviorRepository;
        this.baselineCache = baselineCache;
    }

    @Transactional
    public void activate(LogBaselineSnapshot logBaseline, BehaviorBaselineSnapshot behaviorBaseline) {
        AnomalyGroupKey key = logBaseline != null ? logBaseline.groupKey() : behaviorBaseline.groupKey();
        if (key == null) {
            throw new IllegalArgumentException("baseline group key is required");
        }
        if (logBaseline != null) {
            logRepository.deactivateActive(key.serviceId(), key.endpointId(), key.flowType());
            logRepository.save(BaselineMapper.toEntity(logBaseline));
        }
        if (behaviorBaseline != null) {
            behaviorRepository.deactivateActive(key.serviceId(), key.endpointId(), key.flowType());
            behaviorRepository.save(BaselineMapper.toEntity(behaviorBaseline));
        }
        baselineCache.invalidate(key);
    }
}
