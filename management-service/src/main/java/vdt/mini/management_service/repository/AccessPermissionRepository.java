package vdt.mini.management_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.AccessPermission;

import java.util.List;
import java.util.Optional;

public interface AccessPermissionRepository extends JpaRepository<AccessPermission, String> {
    boolean existsByClientIdAndInboundEndpointId(String clientId, String inboundEndpointId);

    Optional<AccessPermission> findByClientIdAndInboundEndpointId(String clientId, String inboundEndpointId);

    @Query("SELECT permission FROM AccessPermission permission "
            + "JOIN FETCH permission.client client "
            + "JOIN FETCH permission.inboundEndpoint endpoint "
            + "WHERE endpoint.id = :inboundEndpointId "
            + "AND permission.enable = true "
            + "ORDER BY permission.createdAt ASC, permission.id ASC")
    List<AccessPermission> findEnabledByInboundEndpointId(@Param("inboundEndpointId") String inboundEndpointId);

    @Query("SELECT permission FROM AccessPermission permission "
            + "JOIN FETCH permission.client client "
            + "JOIN FETCH permission.inboundEndpoint endpoint "
            + "WHERE endpoint.secureService.id = :serviceId "
            + "AND permission.enable = true "
            + "ORDER BY endpoint.id ASC, permission.createdAt ASC, permission.id ASC")
    List<AccessPermission> findEnabledByServiceId(@Param("serviceId") String serviceId);

    @Query("SELECT permission FROM AccessPermission permission "
            + "JOIN FETCH permission.client client "
            + "JOIN FETCH permission.inboundEndpoint endpoint "
            + "WHERE endpoint.secureService.id = :serviceId "
            + "ORDER BY endpoint.id ASC, permission.createdAt ASC, permission.id ASC")
    List<AccessPermission> findRuntimeByServiceId(@Param("serviceId") String serviceId);

    @Query("SELECT permission FROM AccessPermission permission "
            + "JOIN FETCH permission.client client "
            + "JOIN FETCH permission.inboundEndpoint endpoint "
            + "WHERE permission.id = :id")
    Optional<AccessPermission> findByIdWithClientAndEndpoint(@Param("id") String id);

    @Query("SELECT permission FROM AccessPermission permission "
            + "JOIN permission.client client "
            + "JOIN permission.inboundEndpoint endpoint "
            + "WHERE (:clientId IS NULL OR client.id = :clientId) "
            + "AND (:inboundEndpointId IS NULL OR endpoint.id = :inboundEndpointId) "
            + "AND (:enable IS NULL OR permission.enable = :enable) "
            + "AND (:keywordPattern IS NULL OR LOWER(client.name) LIKE :keywordPattern "
            + "OR LOWER(client.clientKey) LIKE :keywordPattern "
            + "OR LOWER(endpoint.name) LIKE :keywordPattern "
            + "OR LOWER(endpoint.path) LIKE :keywordPattern) "
            + "ORDER BY permission.createdAt DESC")
    Page<AccessPermission> search(@Param("clientId") String clientId,
                                  @Param("inboundEndpointId") String inboundEndpointId,
                                  @Param("enable") Boolean enable,
                                  @Param("keywordPattern") String keywordPattern,
                                  Pageable pageable);
}
