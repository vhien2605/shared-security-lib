package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.entity.SecurityLogBaseline;

import java.util.Optional;

public interface SecurityLogBaselineRepository extends JpaRepository<SecurityLogBaseline, String> {
    Optional<SecurityLogBaseline> findFirstByServiceIdAndEndpointIdAndFlowTypeAndActiveTrue(String serviceId, String endpointId, String flowType);

    @Modifying
    @Transactional
    @Query("update SecurityLogBaseline b set b.active = false where b.serviceId = :serviceId and b.endpointId = :endpointId and b.flowType = :flowType and b.active = true")
    int deactivateActive(@Param("serviceId") String serviceId, @Param("endpointId") String endpointId, @Param("flowType") String flowType);
}
