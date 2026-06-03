package vdt.mini.management_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.event.ClientSecurityConfigEvent;
import vdt.mini.management_service.dto.request.AccessRuleCreateRequest;
import vdt.mini.management_service.dto.response.AccessRuleDeleteResponse;
import vdt.mini.management_service.dto.response.AccessRuleResponse;
import vdt.mini.management_service.entity.AuditLog;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.InboundAccessRuleRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccessRuleService {
    private static final int MAX_PAGE_SIZE = 100;

    private final InboundAccessRuleRepository accessRuleRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClientSecurityEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AccessRuleService(InboundAccessRuleRepository accessRuleRepository,
                             InboundEndpointRepository inboundEndpointRepository,
                             AuditLogRepository auditLogRepository,
                             ClientSecurityEventPublisher eventPublisher,
                             ObjectMapper objectMapper) {
        this.accessRuleRepository = accessRuleRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccessRuleResponse createAccessRule(String inboundEndpointId,
                                               AccessRuleCreateRequest request,
                                               Authentication authentication) {
        validateCreateRequest(request);
        InboundEndpoint endpoint = inboundEndpointRepository.findById(inboundEndpointId)
                .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));

        InboundAccessRule rule = new InboundAccessRule();
        rule.setId(UUID.randomUUID().toString());
        rule.setInboundEndpoint(endpoint);
        rule.setType(parseRuleType(request.getType()));
        rule.setValueType(parseValueType(request.getValueType()));
        rule.setValue(request.getValue().trim());
        rule.setTemporary(Boolean.TRUE.equals(request.getTemporary()));
        rule.setExpiresAt(rule.getTemporary() ? request.getExpiresAt() : null);
        rule.setReason(normalizeBlank(request.getReason()));
        rule.setCreatedBy(actor(authentication));
        InboundAccessRule savedRule = accessRuleRepository.save(rule);

        writeAudit(actor(authentication), "ACCESS_RULE_CREATED", savedRule.getId(), Map.of(
                "inboundEndpointId", inboundEndpointId,
                "type", savedRule.getType(),
                "valueType", savedRule.getValueType(),
                "temporary", savedRule.getTemporary()
        ));
        registerAfterCommit("ACCESS_RULE_CREATED", inboundEndpointId, savedRule.getId());

        return toResponse(savedRule);
    }

    @Transactional(readOnly = true)
    public Page<AccessRuleResponse> listAccessRules(String inboundEndpointId,
                                                    String type,
                                                    String valueType,
                                                    String keyword,
                                                    Pageable pageable) {
        if (!inboundEndpointRepository.existsById(inboundEndpointId)) {
            throw new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND);
        }
        Pageable safePageable = PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));
        return accessRuleRepository.search(inboundEndpointId,
                parseRuleTypeOptional(type),
                parseValueTypeOptional(valueType),
                toKeywordPattern(keyword),
                safePageable).map(this::toResponse);
    }

    @Transactional
    public AccessRuleDeleteResponse deleteAccessRule(String ruleId, Authentication authentication) {
        InboundAccessRule rule = accessRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_RULE_NOT_FOUND));
        String inboundEndpointId = rule.getInboundEndpoint().getId();
        accessRuleRepository.delete(rule);

        writeAudit(actor(authentication), "ACCESS_RULE_DELETED", ruleId, Map.of("inboundEndpointId", inboundEndpointId));
        registerAfterCommit("ACCESS_RULE_DELETED", inboundEndpointId, ruleId);

        return AccessRuleDeleteResponse.builder()
                .message("Access rule deleted successfully")
                .build();
    }

    private void validateCreateRequest(AccessRuleCreateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        requireNotBlank(request.getType(), "type is required");
        requireNotBlank(request.getValueType(), "valueType is required");
        requireNotBlank(request.getValue(), "value is required");
        if (Boolean.TRUE.equals(request.getTemporary()) && request.getExpiresAt() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "expiresAt is required for temporary access rule");
        }
        parseRuleType(request.getType());
        parseValueType(request.getValueType());
    }

    private AccessRuleType parseRuleTypeOptional(String type) {
        return type == null || type.isBlank() ? null : parseRuleType(type);
    }

    private AccessRuleType parseRuleType(String type) {
        try {
            return AccessRuleType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "type must be WHITELIST or BLACKLIST");
        }
    }

    private AccessRuleValueType parseValueTypeOptional(String valueType) {
        return valueType == null || valueType.isBlank() ? null : parseValueType(valueType);
    }

    private AccessRuleValueType parseValueType(String valueType) {
        try {
            AccessRuleValueType parsedValueType = AccessRuleValueType.valueOf(valueType);
            if (parsedValueType == AccessRuleValueType.IP || parsedValueType == AccessRuleValueType.CLIENT_ID) {
                return parsedValueType;
            }
            throw new IllegalArgumentException("Unsupported access rule valueType");
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "valueType must be IP or CLIENT_ID");
        }
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, message);
        }
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toKeywordPattern(String value) {
        String normalizedValue = normalizeBlank(value);
        return normalizedValue == null ? null : "%" + normalizedValue.toLowerCase(Locale.ROOT) + "%";
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "system";
    }

    private void writeAudit(String actor, String action, String entityId, Map<String, Object> payload) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setEntityType("ACCESS_RULE");
        auditLog.setEntityId(entityId);
        try {
            auditLog.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            auditLog.setPayload("{}");
        }
        auditLogRepository.save(auditLog);
    }

    private void registerAfterCommit(String eventType, String inboundEndpointId, String accessRuleId) {
        ClientSecurityConfigEvent event = ClientSecurityConfigEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .inboundEndpointId(inboundEndpointId)
                .accessRuleId(accessRuleId)
                .changedFields(List.of("accessRules"))
                .version(System.currentTimeMillis())
                .build();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(event);
            }
        });
    }

    private AccessRuleResponse toResponse(InboundAccessRule rule) {
        return AccessRuleResponse.builder()
                .id(rule.getId())
                .inboundEndpointId(rule.getInboundEndpoint().getId())
                .type(rule.getType())
                .valueType(rule.getValueType())
                .value(rule.getValue())
                .temporary(rule.getTemporary())
                .expiresAt(rule.getExpiresAt())
                .reason(rule.getReason())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
