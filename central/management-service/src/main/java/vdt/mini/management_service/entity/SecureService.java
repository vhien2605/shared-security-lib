package vdt.mini.management_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service")
public class SecureService extends AbstractAuditable {
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_url", length = 255)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @OneToMany(mappedBy = "secureService")
    private List<InboundEndpoint> inboundEndpoints = new ArrayList<>();

    @OneToMany(mappedBy = "secureService")
    private List<OutboundEndpoint> outboundEndpoints = new ArrayList<>();
}
