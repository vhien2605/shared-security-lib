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
import vdt.mini.management_service.dto.request.AccessRuleCreateRequest;
import vdt.mini.management_service.dto.request.AccessRuleUpdateRequest;
import vdt.mini.management_service.dto.response.AccessRuleDeleteResponse;
import vdt.mini.management_service.dto.response.AccessRuleResponse;
import vdt.mini.management_service.entity.AuditLog;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.InboundAccessRuleRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccessRuleService {
    private static final int MAX_PAGE_SIZE = 100;

    private final InboundAccessRuleRepository accessRuleRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisSettingsSyncService redisSettingsSyncService;
    private final ObjectMapper objectMapper;

    public AccessRuleService(InboundAccessRuleRepository accessRuleRepository,
                              InboundEndpointRepository inboundEndpointRepository,
                              AuditLogRepository auditLogRepository,
                              RedisSettingsSyncService redisSettingsSyncService,
                              ObjectMapper objectMapper) {
        this.accessRuleRepository = accessRuleRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisSettingsSyncService = redisSettingsSyncService;
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
        rule.setEnable(request.getEnable() == null ? Boolean.TRUE : request.getEnable());
        rule.setExpiresAt(rule.getTemporary() ? request.getExpiresAt() : null);
        rule.setReason(normalizeBlank(request.getReason()));
        rule.setCreatedBy(actor(authentication));
        InboundAccessRule savedRule = accessRuleRepository.save(rule);

        writeAudit(actor(authentication), "ACCESS_RULE_CREATED", savedRule.getId(), Map.of(
                "inboundEndpointId", inboundEndpointId,
                "type", savedRule.getType(),
                "valueType", savedRule.getValueType(),
                "enable", savedRule.getEnable(),
                "temporary", savedRule.getTemporary()
        ));
        registerAfterCommit("ACCESS_RULE_CREATED", endpoint, savedRule.getId());

        return toResponse(savedRule);
    }

    @Transactional(readOnly = true)
    public Page<AccessRuleResponse> listAccessRules(String inboundEndpointId,
                                                    String type,
                                                     String valueType,
                                                     Boolean enable,
                                                     String keyword,
                                                     Pageable pageable) {
        if (!inboundEndpointRepository.existsById(inboundEndpointId)) {
            throw new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND);
        }
        Pageable safePageable = toSafePageable(pageable);
        return accessRuleRepository.search(inboundEndpointId,
                parseRuleTypeOptional(type),
                parseValueTypeOptional(valueType),
                enable,
                toKeywordPattern(keyword),
                safePageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AccessRuleResponse> listAllAccessRules(String type,
                                                       String inboundEndpointId,
                                                       String endpointKeyword,
                                                       String valueType,
                                                       Boolean enable,
                                                       String keyword,
                                                       Pageable pageable) {
        Pageable safePageable = toSafePageable(pageable);
        return accessRuleRepository.searchAll(parseRuleTypeOptional(type),
                normalizeBlank(inboundEndpointId),
                toKeywordPattern(endpointKeyword),
                parseValueTypeOptional(valueType),
                enable,
                toKeywordPattern(keyword),
                safePageable).map(this::toResponse);
    }

    @Transactional
    public AccessRuleResponse updateAccessRule(String ruleId,
                                               AccessRuleUpdateRequest request,
                                               Authentication authentication) {
        validateUpdateRequest(request);
        InboundAccessRule rule = accessRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_RULE_NOT_FOUND));
        Boolean oldEnable = rule.getEnable();
        rule.setEnable(request.getEnable());
        InboundAccessRule savedRule = accessRuleRepository.save(rule);

        writeAudit(actor(authentication), "ACCESS_RULE_UPDATED", ruleId, Map.of(
                "inboundEndpointId", savedRule.getInboundEndpoint().getId(),
                "oldEnable", oldEnable,
                "newEnable", savedRule.getEnable()
        ));
        registerAfterCommit("ACCESS_RULE_UPDATED", savedRule.getInboundEndpoint(), savedRule.getId());

        return toResponse(savedRule);
    }

    @Transactional
    public AccessRuleDeleteResponse deleteAccessRule(String ruleId, Authentication authentication) {
        InboundAccessRule rule = accessRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_RULE_NOT_FOUND));
        String inboundEndpointId = rule.getInboundEndpoint().getId();
        InboundEndpoint inboundEndpoint = rule.getInboundEndpoint();
        accessRuleRepository.delete(rule);

        writeAudit(actor(authentication), "ACCESS_RULE_DELETED", ruleId, Map.of("inboundEndpointId", inboundEndpointId));
        registerAfterCommit("ACCESS_RULE_DELETED", inboundEndpoint, ruleId);

        return AccessRuleDeleteResponse.builder()
                .message("Access rule deleted successfully")
                .build();
    }

    private void validateUpdateRequest(AccessRuleUpdateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        if (request.getEnable() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "enable is required");
        }
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
            return AccessRuleValueType.valueOf(valueType);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "valueType must be IP, CIDR, CLIENT_KEY, or HEADER");
        }
    }

    private Pageable toSafePageable(Pageable pageable) {
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));
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

    private void registerAfterCommit(String eventType, InboundEndpoint inboundEndpoint, String accessRuleId) {
        String inboundEndpointId = inboundEndpoint.getId();
        String serviceId = inboundEndpoint.getSecureService() != null ? inboundEndpoint.getSecureService().getId() : null;
        Runnable afterCommit = () -> {
            if (serviceId != null && !serviceId.isBlank()) {
                redisSettingsSyncService.syncInboundToRedis(inboundEndpoint);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.run();
            }
        });
    }

    private AccessRuleResponse toResponse(InboundAccessRule rule) {
        InboundEndpoint endpoint = rule.getInboundEndpoint();
        SecureService secureService = endpoint.getSecureService();
        return AccessRuleResponse.builder()
                .id(rule.getId())
                .inboundEndpointId(endpoint.getId())
                .inboundEndpointName(endpoint.getName())
                .inboundEndpointPath(endpoint.getPath())
                .serviceId(secureService != null ? secureService.getId() : null)
                .serviceName(secureService != null ? secureService.getName() : null)
                .type(rule.getType())
                .valueType(rule.getValueType())
                .value(rule.getValue())
                .temporary(rule.getTemporary())
                .enable(rule.getEnable())
                .expiresAt(rule.getExpiresAt())
                .reason(rule.getReason())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
