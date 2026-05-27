package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.entity.converter.StringListJsonConverter;
import vdt.mini.management_service.util.enums.AlertSeverity;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alert_config")
public class AlertConfig extends AbstractAuditable {
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "channels", columnDefinition = "JSON", nullable = false)
    private List<String> channels;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private AlertSeverity severity = AlertSeverity.WARNING;

    @Column(name = "throttle_minutes", nullable = false)
    private Integer throttleMinutes = 5;

    @OneToMany(mappedBy = "alertConfig")
    private List<InboundEndpoint> inboundEndpoints = new ArrayList<>();

    @OneToMany(mappedBy = "alertConfig")
    private List<OutboundEndpoint> outboundEndpoints = new ArrayList<>();
}
