package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;

import java.util.Optional;

@Service
public class BaselineQueryService {
    private final BaselineCache baselineCache;

    public BaselineQueryService(BaselineCache baselineCache) {
        this.baselineCache = baselineCache;
    }

    public Optional<LogBaselineSnapshot> findLogBaseline(AnomalyGroupKey key) {
        return baselineCache.getLogBaseline(key);
    }

    public Optional<BehaviorBaselineSnapshot> findBehaviorBaseline(AnomalyGroupKey key) {
        return baselineCache.getBehaviorBaseline(key);
    }
}
