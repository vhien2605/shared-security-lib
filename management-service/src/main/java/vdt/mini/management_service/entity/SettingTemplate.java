package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.entity.converter.StringListJsonConverter;
import vdt.mini.management_service.util.enums.AlertSeverity;
import vdt.mini.management_service.util.enums.RollbackStrategy;
import vdt.mini.management_service.util.enums.SettingTemplateLevel;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "setting_template", uniqueConstraints = {
        @UniqueConstraint(name = "uk_setting_template_service", columnNames = {"level", "service_id"})
})
public class SettingTemplate extends AbstractAuditable {

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private SettingTemplateLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private SecureService secureService;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "inbound_rate_limit", nullable = false)
    private Integer inboundRateLimit;
    @Column(name = "inbound_rate_limit_window_seconds", nullable = false)
    private Integer inboundRateLimitWindowSeconds;
    @Column(name = "inbound_timeout_ms", nullable = false)
    private Integer inboundTimeoutMs;
    @Column(name = "inbound_request_size_limit_kb", nullable = false)
    private Integer inboundRequestSizeLimitKb;
    @Column(name = "inbound_response_size_limit_kb", nullable = false)
    private Integer inboundResponseSizeLimitKb;
    @Column(name = "inbound_response_time_threshold_ms", nullable = false)
    private Integer inboundResponseTimeThresholdMs;
    @Column(name = "inbound_log_retention_days", nullable = false)
    private Integer inboundLogRetentionDays;

    @Column(name = "outbound_timeout_ms", nullable = false)
    private Integer outboundTimeoutMs;
    @Column(name = "outbound_retry_count", nullable = false)
    private Integer outboundRetryCount;
    @Column(name = "outbound_retry_backoff_ms", nullable = false)
    private Integer outboundRetryBackoffMs;
    @Column(name = "outbound_response_time_threshold_ms", nullable = false)
    private Integer outboundResponseTimeThresholdMs;
    @Column(name = "outbound_log_retention_days", nullable = false)
    private Integer outboundLogRetentionDays;
    @Enumerated(EnumType.STRING)
    @Column(name = "outbound_rollback_strategy", nullable = false, length = 50)
    private RollbackStrategy outboundRollbackStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_severity", nullable = false, length = 20)
    private AlertSeverity alertSeverity;
    @Column(name = "alert_throttle_minutes", nullable = false)
    private Integer alertThrottleMinutes;
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "alert_channels", columnDefinition = "TEXT", nullable = false)
    private List<String> alertChannels;
}
