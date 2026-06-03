package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.util.enums.AuthType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthConfigRepository extends JpaRepository<AuthConfig, String> {
    List<AuthConfig> findByClientId(String clientId);

    List<AuthConfig> findByIdIn(Collection<String> ids);

    Optional<AuthConfig> findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc(String clientId, AuthType type);

    boolean existsByClientIdAndInboundEndpointIdAndEnabledTrue(String clientId, String inboundEndpointId);

    @Query("SELECT ac FROM AuthConfig ac WHERE ac.client.id = :clientId AND ac.inboundEndpoint.id = :endpointId "
            + "AND ac.enabled = true AND (:excludeId IS NULL OR ac.id <> :excludeId)")
    List<AuthConfig> findEnabledConflicts(@Param("clientId") String clientId,
                                           @Param("endpointId") String endpointId,
                                           @Param("excludeId") String excludeId);

    @Query("SELECT ac FROM AuthConfig ac WHERE ac.client.id = :clientId "
            + "AND (ac.service.id = :serviceId OR ac.inboundEndpoint.secureService.id = :serviceId) "
            + "AND ac.enabled = true AND (:excludeId IS NULL OR ac.id <> :excludeId)")
    List<AuthConfig> findEnabledServiceConflicts(@Param("clientId") String clientId,
                                                  @Param("serviceId") String serviceId,
                                                  @Param("excludeId") String excludeId);

    @Query("SELECT DISTINCT ac FROM AuthConfig ac "
            + "WHERE ac.enabled = true "
            + "AND (ac.service.id = :serviceId OR ac.inboundEndpoint.secureService.id = :serviceId) "
            + "ORDER BY ac.createdAt ASC, ac.id ASC")
    List<AuthConfig> findEnabledByServiceScope(@Param("serviceId") String serviceId);
}
