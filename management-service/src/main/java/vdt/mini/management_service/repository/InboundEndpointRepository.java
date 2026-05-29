package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.InboundEndpoint;

import java.util.List;
import java.util.Optional;

public interface InboundEndpointRepository extends JpaRepository<InboundEndpoint, String> {
    List<InboundEndpoint> findBySecureServiceId(String serviceId);

    @Query("SELECT ie FROM InboundEndpoint ie JOIN FETCH ie.alertConfig WHERE ie.secureService.id = :serviceId")
    List<InboundEndpoint> findBySecureServiceIdWithAlert(@Param("serviceId") String serviceId);

    @Query("SELECT ie FROM InboundEndpoint ie JOIN FETCH ie.alertConfig WHERE ie.id = :id")
    Optional<InboundEndpoint> findByIdWithAlert(@Param("id") String id);

    @Query("SELECT DISTINCT ie FROM InboundEndpoint ie "
         + "JOIN FETCH ie.alertConfig "
         + "LEFT JOIN FETCH ie.authConfigs "
         + "LEFT JOIN FETCH ie.accessRules "
         + "WHERE ie.secureService.id = :serviceId")
    List<InboundEndpoint> findBySecureServiceIdWithAll(@Param("serviceId") String serviceId);
}
