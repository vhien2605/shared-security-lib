package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "in_app_notification")
public class InAppNotification extends AbstractAuditable {
    @Column(name = "title", length = 180, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "severity", length = 20, nullable = false)
    private String severity;

    @Column(name = "anomaly_id", length = 64)
    private String anomalyId;

    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Column(name = "endpoint_id", length = 100)
    private String endpointId;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
