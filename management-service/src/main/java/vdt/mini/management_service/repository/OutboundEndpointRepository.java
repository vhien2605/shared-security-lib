package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.OutboundEndpoint;

import java.util.List;
import java.util.Optional;

public interface OutboundEndpointRepository extends JpaRepository<OutboundEndpoint, String> {
    List<OutboundEndpoint> findBySecureServiceIdOrderByIdAsc(String serviceId);

    List<OutboundEndpoint> findBySecureServiceIdAndEnabledTrueOrderByIdAsc(String serviceId);

    @Query("SELECT oe FROM OutboundEndpoint oe LEFT JOIN FETCH oe.alertConfig WHERE oe.secureService.id = :serviceId AND oe.enabled = true ORDER BY oe.id ASC")
    List<OutboundEndpoint> findBySecureServiceIdWithAlert(@Param("serviceId") String serviceId);

    @Query("SELECT oe FROM OutboundEndpoint oe LEFT JOIN FETCH oe.alertConfig WHERE oe.id = :id AND oe.enabled = true")
    Optional<OutboundEndpoint> findByIdWithAlert(@Param("id") String id);

    @Query("SELECT oe FROM OutboundEndpoint oe LEFT JOIN FETCH oe.alertConfig WHERE oe.id = :id")
    Optional<OutboundEndpoint> findAnyByIdWithAlert(@Param("id") String id);

    @Query("SELECT oe FROM OutboundEndpoint oe WHERE oe.secureService.id = :serviceId ORDER BY oe.id ASC")
    List<OutboundEndpoint> findAllBySecureServiceId(@Param("serviceId") String serviceId);

    @Query("SELECT oe FROM OutboundEndpoint oe LEFT JOIN FETCH oe.alertConfig WHERE oe.secureService.id = :serviceId ORDER BY oe.id ASC")
    List<OutboundEndpoint> findAllBySecureServiceIdWithAlert(@Param("serviceId") String serviceId);
}
