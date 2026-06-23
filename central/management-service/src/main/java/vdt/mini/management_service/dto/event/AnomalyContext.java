package vdt.mini.management_service.dto.event;

public record AnomalyContext(SecurityLogEventMessage event,
                             AnomalyGroupKey groupKey,
                             StaticResultContext staticContext,
                             LogBaselineSnapshot logBaseline,
                             BehaviorBaselineSnapshot behaviorBaseline,
                             RollingWindowSnapshot rollingSnapshot,
                             HistoricalDeviation historicalDeviation,
                             BehaviorDeviation behaviorDeviation,
                             BaselineConfidence confidence) {
}
