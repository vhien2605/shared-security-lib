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
import vdt.mini.management_service.dto.request.AccessPermissionCreateRequest;
import vdt.mini.management_service.dto.request.AccessPermissionUpdateRequest;
import vdt.mini.management_service.dto.response.AccessPermissionDeleteResponse;
import vdt.mini.management_service.dto.response.AccessPermissionResponse;
import vdt.mini.management_service.entity.AccessPermission;
import vdt.mini.management_service.entity.AuditLog;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AccessPermissionRepository;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.util.enums.ClientStatus;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccessPermissionService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccessPermissionRepository accessPermissionRepository;
    private final ClientRepository clientRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClientSecurityEventPublisher eventPublisher;
    private final RedisSettingsSyncService redisSettingsSyncService;
    private final ObjectMapper objectMapper;

    public AccessPermissionService(AccessPermissionRepository accessPermissionRepository,
                                   ClientRepository clientRepository,
                                   InboundEndpointRepository inboundEndpointRepository,
                                   AuditLogRepository auditLogRepository,
                                   ClientSecurityEventPublisher eventPublisher,
                                   RedisSettingsSyncService redisSettingsSyncService,
                                   ObjectMapper objectMapper) {
        this.accessPermissionRepository = accessPermissionRepository;
        this.clientRepository = clientRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.redisSettingsSyncService = redisSettingsSyncService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccessPermissionResponse createPermission(AccessPermissionCreateRequest request,
                                                     Authentication authentication) {
        validateCreateRequest(request);
        String clientId = request.getClientId().trim();
        String inboundEndpointId = request.getInboundEndpointId().trim();
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_NOT_FOUND));
        if (client.getStatus() == ClientStatus.REVOKED) {
            throw new AppException(ErrorCode.INVALID_CLIENT_STATUS_TRANSITION, "Cannot grant access to revoked client");
        }
        InboundEndpoint endpoint = inboundEndpointRepository.findById(inboundEndpointId)
                .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));
        if (accessPermissionRepository.existsByClientIdAndInboundEndpointId(clientId, inboundEndpointId)) {
            throw new AppException(ErrorCode.ACCESS_PERMISSION_CONFLICT);
        }

        AccessPermission permission = new AccessPermission();
        permission.setId(UUID.randomUUID().toString());
        permission.setClient(client);
        permission.setInboundEndpoint(endpoint);
        permission.setEnable(request.getEnable() == null ? Boolean.TRUE : request.getEnable());
        AccessPermission savedPermission = accessPermissionRepository.save(permission);

        writeAudit(actor(authentication), "ACCESS_PERMISSION_CREATED", savedPermission.getId(), Map.of(
                "clientId", clientId,
                "inboundEndpointId", inboundEndpointId,
                "enable", savedPermission.getEnable()
        ));
        registerAfterCommit("ACCESS_PERMISSION_CREATED", savedPermission);

        return toResponse(savedPermission);
    }

    @Transactional(readOnly = true)
    public Page<AccessPermissionResponse> listPermissions(String clientId,
                                                          String inboundEndpointId,
                                                          Boolean enable,
                                                          String keyword,
                                                          Pageable pageable) {
        Pageable safePageable = PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));
        return accessPermissionRepository.search(normalizeBlank(clientId),
                normalizeBlank(inboundEndpointId),
                enable,
                toKeywordPattern(keyword),
                safePageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AccessPermissionResponse getPermission(String permissionId) {
        return toResponse(loadPermission(permissionId));
    }

    @Transactional
    public AccessPermissionResponse updatePermission(String permissionId,
                                                     AccessPermissionUpdateRequest request,
                                                     Authentication authentication) {
        validateUpdateRequest(request);
        AccessPermission permission = loadPermission(permissionId);
        if (Boolean.TRUE.equals(request.getEnable()) && permission.getClient().getStatus() == ClientStatus.REVOKED) {
            throw new AppException(ErrorCode.INVALID_CLIENT_STATUS_TRANSITION, "Cannot enable access for revoked client");
        }
        Boolean oldEnable = permission.getEnable();
        permission.setEnable(request.getEnable());
        AccessPermission savedPermission = accessPermissionRepository.save(permission);

        writeAudit(actor(authentication), "ACCESS_PERMISSION_UPDATED", savedPermission.getId(), Map.of(
                "clientId", savedPermission.getClient().getId(),
                "inboundEndpointId", savedPermission.getInboundEndpoint().getId(),
                "oldEnable", oldEnable,
                "newEnable", savedPermission.getEnable()
        ));
        registerAfterCommit("ACCESS_PERMISSION_UPDATED", savedPermission);

        return toResponse(savedPermission);
    }

    @Transactional
    public AccessPermissionDeleteResponse deletePermission(String permissionId, Authentication authentication) {
        AccessPermission permission = loadPermission(permissionId);
        String clientId = permission.getClient().getId();
        String inboundEndpointId = permission.getInboundEndpoint().getId();
        accessPermissionRepository.delete(permission);

        writeAudit(actor(authentication), "ACCESS_PERMISSION_DELETED", permissionId, Map.of(
                "clientId", clientId,
                "inboundEndpointId", inboundEndpointId
        ));
        registerAfterCommit("ACCESS_PERMISSION_DELETED", permission);

        return AccessPermissionDeleteResponse.builder()
                .message("Access permission deleted successfully")
                .build();
    }

    private AccessPermission loadPermission(String permissionId) {
        requireNotBlank(permissionId, "permissionId is required");
        return accessPermissionRepository.findByIdWithClientAndEndpoint(permissionId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_PERMISSION_NOT_FOUND));
    }

    private void validateCreateRequest(AccessPermissionCreateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        requireNotBlank(request.getClientId(), "clientId is required");
        requireNotBlank(request.getInboundEndpointId(), "inboundEndpointId is required");
    }

    private void validateUpdateRequest(AccessPermissionUpdateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        if (request.getEnable() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "enable is required");
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
        auditLog.setEntityType("ACCESS_PERMISSION");
        auditLog.setEntityId(entityId);
        try {
            auditLog.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            auditLog.setPayload("{}");
        }
        auditLogRepository.save(auditLog);
    }

    private void registerAfterCommit(String eventType, AccessPermission permission) {
        String clientId = permission.getClient().getId();
        String inboundEndpointId = permission.getInboundEndpoint().getId();
        String serviceId = permission.getInboundEndpoint().getSecureService() != null
                ? permission.getInboundEndpoint().getSecureService().getId()
                : null;
        ClientSecurityConfigEvent event = ClientSecurityConfigEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .clientId(clientId)
                .inboundEndpointId(inboundEndpointId)
                .changedFields(List.of("permissions"))
                .version(System.currentTimeMillis())
                .build();
        Runnable afterCommit = () -> {
            eventPublisher.publish(event);
            if (serviceId != null && !serviceId.isBlank()) {
                redisSettingsSyncService.syncAllEndpointsOfService(serviceId);
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

    private AccessPermissionResponse toResponse(AccessPermission permission) {
        Client client = permission.getClient();
        InboundEndpoint endpoint = permission.getInboundEndpoint();
        SecureService secureService = endpoint.getSecureService();
        return AccessPermissionResponse.builder()
                .id(permission.getId())
                .clientId(client.getId())
                .clientKey(client.getClientKey())
                .clientName(client.getName())
                .inboundEndpointId(endpoint.getId())
                .inboundEndpointName(endpoint.getName())
                .inboundEndpointPath(endpoint.getPath())
                .serviceId(secureService != null ? secureService.getId() : null)
                .serviceName(secureService != null ? secureService.getName() : null)
                .enable(permission.getEnable())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
