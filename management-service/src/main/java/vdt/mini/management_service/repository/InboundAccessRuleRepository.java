package vdt.mini.management_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;

public interface InboundAccessRuleRepository extends JpaRepository<InboundAccessRule, String> {
    @Query("SELECT rule FROM InboundAccessRule rule "
            + "WHERE rule.inboundEndpoint.id = :inboundEndpointId "
            + "AND (:type IS NULL OR rule.type = :type) "
            + "AND (:valueType IS NULL OR rule.valueType = :valueType) "
            + "AND (:keywordPattern IS NULL OR LOWER(rule.value) LIKE :keywordPattern) "
            + "ORDER BY rule.createdAt DESC")
    Page<InboundAccessRule> search(@Param("inboundEndpointId") String inboundEndpointId,
                                   @Param("type") AccessRuleType type,
                                   @Param("valueType") AccessRuleValueType valueType,
                                   @Param("keywordPattern") String keywordPattern,
                                   Pageable pageable);
}
