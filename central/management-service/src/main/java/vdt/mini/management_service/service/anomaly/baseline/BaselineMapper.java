package vdt.mini.management_service.service.anomaly.baseline;

import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.entity.SecurityBehaviorBaseline;
import vdt.mini.management_service.entity.SecurityLogBaseline;

import java.util.UUID;

final class BaselineMapper {
    private BaselineMapper() {
    }

    static LogBaselineSnapshot toSnapshot(SecurityLogBaseline entity) {
        if (entity == null) return null;
        return new LogBaselineSnapshot(new AnomalyGroupKey(entity.getServiceId(), entity.getEndpointId(), entity.getFlowType()), entity.getSampleCount(),
                entity.getMedianDurationMs(), entity.getP95DurationMs(), entity.getP99DurationMs(), entity.getDurationIqr(),
                entity.getMedianRequestSizeBytes(), entity.getP95RequestSizeBytes(), entity.getRequestSizeIqr(),
                entity.getMedianResponseSizeBytes(), entity.getP95ResponseSizeBytes(), entity.getResponseSizeIqr(),
                entity.getMedianMessageSizeBytes(), entity.getP95MessageSizeBytes(), entity.getMessageSizeIqr(),
                entity.getMedianRetryAttempt(), entity.getP95RetryAttempt(), entity.getRetryAttemptIqr(),
                entity.getKnownClients(), entity.getKnownSourceIps(), entity.getKnownErrorCodes(), entity.getBaselineVersion(), entity.getCalculatedAt(), entity.isActive());
    }

    static SecurityLogBaseline toEntity(LogBaselineSnapshot snapshot) {
        SecurityLogBaseline entity = new SecurityLogBaseline();
        entity.setId(UUID.randomUUID().toString());
        entity.setServiceId(snapshot.groupKey().serviceId());
        entity.setEndpointId(snapshot.groupKey().endpointId());
        entity.setFlowType(snapshot.groupKey().flowType());
        entity.setSampleCount(snapshot.sampleCount());
        entity.setMedianDurationMs(snapshot.medianDurationMs());
        entity.setP95DurationMs(snapshot.p95DurationMs());
        entity.setP99DurationMs(snapshot.p99DurationMs());
        entity.setDurationIqr(snapshot.durationIqr());
        entity.setMedianRequestSizeBytes(snapshot.medianRequestSizeBytes());
        entity.setP95RequestSizeBytes(snapshot.p95RequestSizeBytes());
        entity.setRequestSizeIqr(snapshot.requestSizeIqr());
        entity.setMedianResponseSizeBytes(snapshot.medianResponseSizeBytes());
        entity.setP95ResponseSizeBytes(snapshot.p95ResponseSizeBytes());
        entity.setResponseSizeIqr(snapshot.responseSizeIqr());
        entity.setMedianMessageSizeBytes(snapshot.medianMessageSizeBytes());
        entity.setP95MessageSizeBytes(snapshot.p95MessageSizeBytes());
        entity.setMessageSizeIqr(snapshot.messageSizeIqr());
        entity.setMedianRetryAttempt(snapshot.medianRetryAttempt());
        entity.setP95RetryAttempt(snapshot.p95RetryAttempt());
        entity.setRetryAttemptIqr(snapshot.retryAttemptIqr());
        entity.setKnownClients(snapshot.knownClients());
        entity.setKnownSourceIps(snapshot.knownSourceIps());
        entity.setKnownErrorCodes(snapshot.knownErrorCodes());
        entity.setBaselineVersion(snapshot.baselineVersion());
        entity.setCalculatedAt(snapshot.calculatedAt());
        entity.setActive(snapshot.active());
        return entity;
    }

    static BehaviorBaselineSnapshot toSnapshot(SecurityBehaviorBaseline entity) {
        if (entity == null) return null;
        return new BehaviorBaselineSnapshot(new AnomalyGroupKey(entity.getServiceId(), entity.getEndpointId(), entity.getFlowType()), entity.getWindowCount(),
                entity.getMedianRequestCountLast5m(), entity.getRequestCountLast5mIqr(), entity.getMedianFailureRateLast5m(), entity.getFailureRateLast5mIqr(),
                entity.getMedianDeniedRateLast5m(), entity.getDeniedRateLast5mIqr(), entity.getMedianRetryRateLast5m(), entity.getRetryRateLast5mIqr(),
                entity.getMedianP95DurationLast5m(), entity.getP95DurationLast5mIqr(), entity.getMedianAvgRequestSizeLast5m(), entity.getAvgRequestSizeLast5mIqr(),
                entity.getMedianAvgResponseSizeLast5m(), entity.getAvgResponseSizeLast5mIqr(), entity.getMedianAvgMessageSizeLast5m(), entity.getAvgMessageSizeLast5mIqr(),
                entity.getMedianUniqueClientCountLast5m(), entity.getUniqueClientCountLast5mIqr(), entity.getMedianUniqueSourceIpCountLast5m(), entity.getUniqueSourceIpCountLast5mIqr(),
                entity.getMedianUniqueErrorCodeCountLast5m(), entity.getUniqueErrorCodeCountLast5mIqr(), entity.getBaselineVersion(), entity.getCalculatedAt(), entity.isActive());
    }

    static SecurityBehaviorBaseline toEntity(BehaviorBaselineSnapshot snapshot) {
        SecurityBehaviorBaseline entity = new SecurityBehaviorBaseline();
        entity.setId(UUID.randomUUID().toString());
        entity.setServiceId(snapshot.groupKey().serviceId());
        entity.setEndpointId(snapshot.groupKey().endpointId());
        entity.setFlowType(snapshot.groupKey().flowType());
        entity.setWindowCount(snapshot.windowCount());
        entity.setMedianRequestCountLast5m(snapshot.medianRequestCountLast5m());
        entity.setRequestCountLast5mIqr(snapshot.requestCountLast5mIqr());
        entity.setMedianFailureRateLast5m(snapshot.medianFailureRateLast5m());
        entity.setFailureRateLast5mIqr(snapshot.failureRateLast5mIqr());
        entity.setMedianDeniedRateLast5m(snapshot.medianDeniedRateLast5m());
        entity.setDeniedRateLast5mIqr(snapshot.deniedRateLast5mIqr());
        entity.setMedianRetryRateLast5m(snapshot.medianRetryRateLast5m());
        entity.setRetryRateLast5mIqr(snapshot.retryRateLast5mIqr());
        entity.setMedianP95DurationLast5m(snapshot.medianP95DurationLast5m());
        entity.setP95DurationLast5mIqr(snapshot.p95DurationLast5mIqr());
        entity.setMedianAvgRequestSizeLast5m(snapshot.medianAvgRequestSizeLast5m());
        entity.setAvgRequestSizeLast5mIqr(snapshot.avgRequestSizeLast5mIqr());
        entity.setMedianAvgResponseSizeLast5m(snapshot.medianAvgResponseSizeLast5m());
        entity.setAvgResponseSizeLast5mIqr(snapshot.avgResponseSizeLast5mIqr());
        entity.setMedianAvgMessageSizeLast5m(snapshot.medianAvgMessageSizeLast5m());
        entity.setAvgMessageSizeLast5mIqr(snapshot.avgMessageSizeLast5mIqr());
        entity.setMedianUniqueClientCountLast5m(snapshot.medianUniqueClientCountLast5m());
        entity.setUniqueClientCountLast5mIqr(snapshot.uniqueClientCountLast5mIqr());
        entity.setMedianUniqueSourceIpCountLast5m(snapshot.medianUniqueSourceIpCountLast5m());
        entity.setUniqueSourceIpCountLast5mIqr(snapshot.uniqueSourceIpCountLast5mIqr());
        entity.setMedianUniqueErrorCodeCountLast5m(snapshot.medianUniqueErrorCodeCountLast5m());
        entity.setUniqueErrorCodeCountLast5mIqr(snapshot.uniqueErrorCodeCountLast5mIqr());
        entity.setBaselineVersion(snapshot.baselineVersion());
        entity.setCalculatedAt(snapshot.calculatedAt());
        entity.setActive(snapshot.active());
        return entity;
    }
}
