package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "security_behavior_baseline",
        uniqueConstraints = @UniqueConstraint(name = "uk_security_behavior_baseline_version", columnNames = {"service_id", "endpoint_id", "flow_type", "baseline_version"}),
        indexes = @Index(name = "idx_security_behavior_baseline_active", columnList = "service_id, endpoint_id, flow_type, active"))
public class SecurityBehaviorBaseline extends AbstractAuditable {
    @Column(name = "service_id", nullable = false)
    private String serviceId;
    @Column(name = "endpoint_id", nullable = false)
    private String endpointId;
    @Column(name = "flow_type", nullable = false)
    private String flowType;
    private long windowCount;
    private Double medianRequestCountLast5m;
    private Double requestCountLast5mIqr;
    private Double medianFailureRateLast5m;
    private Double failureRateLast5mIqr;
    private Double medianDeniedRateLast5m;
    private Double deniedRateLast5mIqr;
    private Double medianRetryRateLast5m;
    private Double retryRateLast5mIqr;
    private Double medianP95DurationLast5m;
    private Double p95DurationLast5mIqr;
    private Double medianAvgRequestSizeLast5m;
    private Double avgRequestSizeLast5mIqr;
    private Double medianAvgResponseSizeLast5m;
    private Double avgResponseSizeLast5mIqr;
    private Double medianAvgMessageSizeLast5m;
    private Double avgMessageSizeLast5mIqr;
    private Double medianUniqueClientCountLast5m;
    private Double uniqueClientCountLast5mIqr;
    private Double medianUniqueSourceIpCountLast5m;
    private Double uniqueSourceIpCountLast5mIqr;
    private Double medianUniqueErrorCodeCountLast5m;
    private Double uniqueErrorCodeCountLast5mIqr;
    @Column(name = "baseline_version", nullable = false)
    private String baselineVersion;
    private Instant calculatedAt;
    private boolean active;
}
