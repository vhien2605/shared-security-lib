package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.EndpointStatus;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inbound_endpoint")
public class InboundEndpoint extends AbstractAuditable {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private SecureService secureService;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "path", length = 255)
    private String path;

    @Column(name = "topic", length = 255)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10)
    private EndpointMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 20, nullable = false)
    private EndpointProtocol protocol;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20) default 'ACTIVE'")
    private EndpointStatus status = EndpointStatus.ACTIVE;

    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Column(name = "rate_limit_window_seconds", nullable = false)
    private Integer rateLimitWindowSeconds = 60;

    @Column(name = "request_size_limit_kb")
    private Integer requestSizeLimitKb;

    @Column(name = "response_size_limit_kb")
    private Integer responseSizeLimitKb;

    @Column(name = "response_time_threshold_ms")
    private Integer responseTimeThresholdMs;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 30000;

    @Column(name = "log_retention_days", nullable = false)
    private Integer logRetentionDays = 30;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_config_id")
    private AlertConfig alertConfig;

    @OneToMany(mappedBy = "inboundEndpoint")
    private Set<InboundAccessRule> accessRules = new LinkedHashSet<>();

    @OneToMany(mappedBy = "inboundEndpoint")
    private Set<AuthConfig> authConfigs = new LinkedHashSet<>();

    @OneToMany(mappedBy = "inboundEndpoint")
    private Set<AccessPermission> accessPermissions = new LinkedHashSet<>();
}
