package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.OutboundEndpoint;

import java.util.List;
import java.util.Optional;

public interface OutboundEndpointRepository extends JpaRepository<OutboundEndpoint, String> {
    List<OutboundEndpoint> findBySecureServiceId(String serviceId);

    @Query("SELECT oe FROM OutboundEndpoint oe JOIN FETCH oe.alertConfig WHERE oe.secureService.id = :serviceId")
    List<OutboundEndpoint> findBySecureServiceIdWithAlert(@Param("serviceId") String serviceId);

    @Query("SELECT oe FROM OutboundEndpoint oe JOIN FETCH oe.alertConfig WHERE oe.id = :id")
    Optional<OutboundEndpoint> findByIdWithAlert(@Param("id") String id);
}
