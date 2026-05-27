package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.RollbackStrategy;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbound_endpoint")
public class OutboundEndpoint extends AbstractAuditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private SecureService secureService;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "target_url", length = 255)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10)
    private EndpointMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 20, nullable = false)
    private EndpointProtocol protocol;

    @Column(name = "response_time_threshold_ms")
    private Integer responseTimeThresholdMs;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 30000;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 3;

    @Column(name = "retry_backoff_ms", nullable = false)
    private Integer retryBackoffMs = 1000;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollback_strategy", length = 50)
    private RollbackStrategy rollbackStrategy;

    @Column(name = "log_retention_days", nullable = false)
    private Integer logRetentionDays = 30;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_config_id")
    private AlertConfig alertConfig;
}
