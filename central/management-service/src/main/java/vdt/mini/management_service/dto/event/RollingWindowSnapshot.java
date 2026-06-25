package vdt.mini.management_service.dto.event;

import java.time.Instant;

public record RollingWindowSnapshot(Instant windowStart,
                                    Instant windowEnd,
                                    int windowSampleCount,
                                    long requestCountLast5m,
                                    long failedCountLast5m,
                                    long deniedCountLast5m,
                                    long retryCountLast5m,
                                    double failureRateLast5m,
                                    double deniedRateLast5m,
                                    double retryRateLast5m,
                                    Double avgDurationLast5m,
                                    Double p50DurationLast5m,
                                    Double p95DurationLast5m,
                                    Double maxDurationLast5m,
                                    Double avgRequestSizeLast5m,
                                    Double avgResponseSizeLast5m,
                                    Double avgMessageSizeLast5m,
                                    int uniqueClientCountLast5m,
                                    int uniqueSourceIpCountLast5m,
                                    int uniqueErrorCodeCountLast5m,
                                    String dominantErrorCode,
                                    double dominantErrorCodeShareLast5m,
                                    Instant lastSeenTimestamp) {
    public static RollingWindowSnapshot empty(Instant windowStart, Instant windowEnd) {
        return new RollingWindowSnapshot(windowStart, windowEnd, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, 0, 0, 0, null, 0, null);
    }
}
