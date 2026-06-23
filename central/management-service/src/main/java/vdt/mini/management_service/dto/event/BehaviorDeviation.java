package vdt.mini.management_service.dto.event;

public record BehaviorDeviation(Double requestCountRobustZ,
                                Double failureRateRobustZ,
                                Double deniedRateRobustZ,
                                Double retryRateRobustZ,
                                Double p95DurationRobustZ,
                                Double avgRequestSizeRobustZ,
                                Double avgResponseSizeRobustZ,
                                Double avgMessageSizeRobustZ,
                                Double uniqueClientCountRobustZ,
                                Double uniqueSourceIpCountRobustZ,
                                Double uniqueErrorCodeCountRobustZ) {
}
