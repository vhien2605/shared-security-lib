package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.InboundEndpoint;

public interface InboundEndpointRepository extends JpaRepository<InboundEndpoint, String> {
}
