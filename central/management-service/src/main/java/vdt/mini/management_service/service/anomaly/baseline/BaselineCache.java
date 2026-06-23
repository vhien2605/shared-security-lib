package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.repository.SecurityBehaviorBaselineRepository;
import vdt.mini.management_service.repository.SecurityLogBaselineRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class BaselineCache {
    private final SecurityLogBaselineRepository logRepository;
    private final SecurityBehaviorBaselineRepository behaviorRepository;
    private final ConcurrentMap<AnomalyGroupKey, Optional<LogBaselineSnapshot>> logCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnomalyGroupKey, Optional<BehaviorBaselineSnapshot>> behaviorCache = new ConcurrentHashMap<>();

    public BaselineCache(SecurityLogBaselineRepository logRepository, SecurityBehaviorBaselineRepository behaviorRepository) {
        this.logRepository = logRepository;
        this.behaviorRepository = behaviorRepository;
    }

    public Optional<LogBaselineSnapshot> getLogBaseline(AnomalyGroupKey key) {
        return logCache.computeIfAbsent(key, this::loadLogBaseline);
    }

    public Optional<BehaviorBaselineSnapshot> getBehaviorBaseline(AnomalyGroupKey key) {
        return behaviorCache.computeIfAbsent(key, this::loadBehaviorBaseline);
    }

    public void invalidate(AnomalyGroupKey key) {
        logCache.remove(key);
        behaviorCache.remove(key);
    }

    private Optional<LogBaselineSnapshot> loadLogBaseline(AnomalyGroupKey key) {
        return logRepository.findFirstByServiceIdAndEndpointIdAndFlowTypeAndActiveTrue(key.serviceId(), key.endpointId(), key.flowType()).map(BaselineMapper::toSnapshot);
    }

    private Optional<BehaviorBaselineSnapshot> loadBehaviorBaseline(AnomalyGroupKey key) {
        return behaviorRepository.findFirstByServiceIdAndEndpointIdAndFlowTypeAndActiveTrue(key.serviceId(), key.endpointId(), key.flowType()).map(BaselineMapper::toSnapshot);
    }
}
