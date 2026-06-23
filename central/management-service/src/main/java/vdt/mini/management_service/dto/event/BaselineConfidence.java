package vdt.mini.management_service.dto.event;

public record BaselineConfidence(boolean hasHistoricalLogBaseline,
                                 boolean hasHistoricalBehaviorBaseline,
                                 boolean hasWindowContext,
                                 boolean windowSampleSufficient) {
}
