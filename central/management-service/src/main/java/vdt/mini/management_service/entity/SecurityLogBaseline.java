package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.entity.converter.StringListJsonConverter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "security_log_baseline",
        uniqueConstraints = @UniqueConstraint(name = "uk_security_log_baseline_version", columnNames = {"service_id", "endpoint_id", "flow_type", "baseline_version"}),
        indexes = @Index(name = "idx_security_log_baseline_active", columnList = "service_id, endpoint_id, flow_type, active"))
public class SecurityLogBaseline extends AbstractAuditable {
    @Column(name = "service_id", nullable = false)
    private String serviceId;
    @Column(name = "endpoint_id", nullable = false)
    private String endpointId;
    @Column(name = "flow_type", nullable = false)
    private String flowType;
    private long sampleCount;
    private Double medianDurationMs;
    private Double p95DurationMs;
    private Double p99DurationMs;
    private Double durationIqr;
    private Double medianRequestSizeBytes;
    private Double p95RequestSizeBytes;
    private Double requestSizeIqr;
    private Double medianResponseSizeBytes;
    private Double p95ResponseSizeBytes;
    private Double responseSizeIqr;
    private Double medianMessageSizeBytes;
    private Double p95MessageSizeBytes;
    private Double messageSizeIqr;
    private Double medianRetryAttempt;
    private Double p95RetryAttempt;
    private Double retryAttemptIqr;
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> knownClients;
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> knownSourceIps;
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> knownErrorCodes;
    @Column(name = "baseline_version", nullable = false)
    private String baselineVersion;
    private Instant calculatedAt;
    private boolean active;
}
