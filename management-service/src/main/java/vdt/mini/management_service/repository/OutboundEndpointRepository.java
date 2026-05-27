package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.OutboundEndpoint;

public interface OutboundEndpointRepository extends JpaRepository<OutboundEndpoint, String> {
}
