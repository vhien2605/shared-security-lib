package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.entity.SecurityBehaviorBaseline;

import java.util.Optional;

public interface SecurityBehaviorBaselineRepository extends JpaRepository<SecurityBehaviorBaseline, String> {
    Optional<SecurityBehaviorBaseline> findFirstByServiceIdAndEndpointIdAndFlowTypeAndActiveTrue(String serviceId, String endpointId, String flowType);

    @Modifying
    @Transactional
    @Query("update SecurityBehaviorBaseline b set b.active = false where b.serviceId = :serviceId and b.endpointId = :endpointId and b.flowType = :flowType and b.active = true")
    int deactivateActive(@Param("serviceId") String serviceId, @Param("endpointId") String endpointId, @Param("flowType") String flowType);
}
