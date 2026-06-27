package vdt.mini.management_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
            + "AND (:enable IS NULL OR rule.enable = :enable) "
            + "AND (:keywordPattern IS NULL OR LOWER(rule.value) LIKE :keywordPattern) "
            + "ORDER BY rule.createdAt DESC")
    @EntityGraph(attributePaths = {"inboundEndpoint", "inboundEndpoint.secureService"})
    Page<InboundAccessRule> search(@Param("inboundEndpointId") String inboundEndpointId,
                                     @Param("type") AccessRuleType type,
                                     @Param("valueType") AccessRuleValueType valueType,
                                     @Param("enable") Boolean enable,
                                     @Param("keywordPattern") String keywordPattern,
                                     Pageable pageable);

    @Query("SELECT rule FROM InboundAccessRule rule "
            + "JOIN rule.inboundEndpoint endpoint "
            + "LEFT JOIN endpoint.secureService service "
            + "WHERE (:type IS NULL OR rule.type = :type) "
            + "AND (:inboundEndpointId IS NULL OR endpoint.id = :inboundEndpointId) "
            + "AND (:endpointKeywordPattern IS NULL OR LOWER(endpoint.id) LIKE :endpointKeywordPattern "
            + "OR LOWER(endpoint.name) LIKE :endpointKeywordPattern) "
            + "AND (:valueType IS NULL OR rule.valueType = :valueType) "
            + "AND (:enable IS NULL OR rule.enable = :enable) "
            + "AND (:keywordPattern IS NULL OR LOWER(rule.value) LIKE :keywordPattern "
            + "OR LOWER(rule.reason) LIKE :keywordPattern "
            + "OR LOWER(service.name) LIKE :keywordPattern "
            + "OR LOWER(endpoint.id) LIKE :keywordPattern "
            + "OR LOWER(service.id) LIKE :keywordPattern) "
            + "ORDER BY rule.createdAt DESC")
    @EntityGraph(attributePaths = {"inboundEndpoint", "inboundEndpoint.secureService"})
    Page<InboundAccessRule> searchAll(@Param("type") AccessRuleType type,
                                      @Param("inboundEndpointId") String inboundEndpointId,
                                      @Param("endpointKeywordPattern") String endpointKeywordPattern,
                                      @Param("valueType") AccessRuleValueType valueType,
                                      @Param("enable") Boolean enable,
                                      @Param("keywordPattern") String keywordPattern,
                                      Pageable pageable);
}
