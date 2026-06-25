package vdt.mini.management_service.dto.event;

import java.time.Instant;

public record BehaviorBaselineSnapshot(AnomalyGroupKey groupKey,
                                       long windowCount,
                                       Double medianRequestCountLast5m,
                                       Double requestCountLast5mIqr,
                                       Double medianFailureRateLast5m,
                                       Double failureRateLast5mIqr,
                                       Double medianDeniedRateLast5m,
                                       Double deniedRateLast5mIqr,
                                       Double medianRetryRateLast5m,
                                       Double retryRateLast5mIqr,
                                       Double medianP95DurationLast5m,
                                       Double p95DurationLast5mIqr,
                                       Double medianAvgRequestSizeLast5m,
                                       Double avgRequestSizeLast5mIqr,
                                       Double medianAvgResponseSizeLast5m,
                                       Double avgResponseSizeLast5mIqr,
                                       Double medianAvgMessageSizeLast5m,
                                       Double avgMessageSizeLast5mIqr,
                                       Double medianUniqueClientCountLast5m,
                                       Double uniqueClientCountLast5mIqr,
                                       Double medianUniqueSourceIpCountLast5m,
                                       Double uniqueSourceIpCountLast5mIqr,
                                       Double medianUniqueErrorCodeCountLast5m,
                                       Double uniqueErrorCodeCountLast5mIqr,
                                       String baselineVersion,
                                       Instant calculatedAt,
                                       boolean active) {
}
