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
import vdt.mini.management_service.dto.request.ClientAuthConfigChangesRequest;
import vdt.mini.management_service.dto.request.ClientAuthConfigCreateRequest;
import vdt.mini.management_service.dto.request.ClientAuthConfigUpdateRequest;
import vdt.mini.management_service.dto.request.ClientCreateRequest;
import vdt.mini.management_service.dto.request.ClientUpdateRequest;
import vdt.mini.management_service.dto.response.ClientAuthConfigChangeItemResponse;
import vdt.mini.management_service.dto.response.ClientAuthConfigChangesResponse;
import vdt.mini.management_service.dto.response.ClientAuthConfigResponse;
import vdt.mini.management_service.dto.response.ClientCreateResponse;
import vdt.mini.management_service.dto.response.ClientCredentialResponse;
import vdt.mini.management_service.dto.response.ClientDetailResponse;
import vdt.mini.management_service.dto.response.ClientListItemResponse;
import vdt.mini.management_service.dto.response.ClientUpdateResponse;
import vdt.mini.management_service.entity.AuditLog;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.AuthType;
import vdt.mini.management_service.util.enums.ClientStatus;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ClientSecurityService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CLIENT_CODE_RANDOM_LENGTH = 8;
    private static final int CLIENT_CODE_MAX_ATTEMPTS = 10;
    private static final String CLIENT_CODE_PREFIX = "CLIENT-";
    private static final String HMAC_SHA256 = "HMAC_SHA256";
    private static final String HMAC_SHA256_JCA = "HmacSHA256";
    private static final String HMAC_SHA384 = "HMAC_SHA384";
    private static final String HMAC_SHA384_JCA = "HmacSHA384";
    private static final String HMAC_SHA512 = "HMAC_SHA512";
    private static final String HMAC_SHA512_JCA = "HmacSHA512";

    private final ClientRepository clientRepository;
    private final AuthConfigRepository authConfigRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final ServiceRepository serviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClientCredentialService credentialService;
    private final ClientSecurityEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ClientSecurityService(ClientRepository clientRepository,
                                 AuthConfigRepository authConfigRepository,
                                 InboundEndpointRepository inboundEndpointRepository,
                                 ServiceRepository serviceRepository,
                                 AuditLogRepository auditLogRepository,
                                 ClientCredentialService credentialService,
                                 ClientSecurityEventPublisher eventPublisher,
                                 ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.authConfigRepository = authConfigRepository;
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.serviceRepository = serviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.credentialService = credentialService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<ClientListItemResponse> listClients(String keyword, String status, Pageable pageable) {
        ClientStatus parsedStatus = parseStatus(status, false);
        Pageable safePageable = PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));
        return clientRepository.search(toKeywordPattern(keyword), parsedStatus, safePageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse getClient(String clientId) {
        Client client = clientRepository.findByIdWithAuthConfigs(clientId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_NOT_FOUND));
        return toDetail(client);
    }

    @Transactional
    public ClientCreateResponse createClient(ClientCreateRequest request, Authentication authentication) {
        validateCreateRequest(request);
        String clientCode = generateUniqueClientCode();

        Client client = new Client();
        client.setId(UUID.randomUUID().toString());
        client.setClientKey(clientCode);
        client.setName(request.getName().trim());
        client.setDescription(normalizeBlank(request.getDescription()));
        client.setContactEmail(normalizeBlank(request.getContactEmail()));
        client.setStatus(parseStatus(request.getStatus(), true));
        clientRepository.save(client);

        List<ClientCredentialResponse> credentials = new ArrayList<>();
        List<String> authConfigIds = new ArrayList<>();
        List<String> serviceIds = new ArrayList<>();
        for (ClientAuthConfigCreateRequest authConfigRequest : safeList(request.getAuthConfigs())) {
            AuthConfig authConfig = createAuthConfig(client, authConfigRequest, credentials);
            authConfigRepository.save(authConfig);
            authConfigIds.add(authConfig.getId());
            serviceIds.add(resolveAuthConfigService(authConfig).getId());
        }
        writeAudit(actor(authentication), "CLIENT_CREATED", client.getId(), Map.of("authConfigIds", authConfigIds));
        registerAfterCommit("CLIENT_CREATED", client.getId(), authConfigIds, serviceIds, List.of("client", "authConfigs"));

        return ClientCreateResponse.builder()
                .clientId(client.getId())
                .clientCode(client.getClientKey())
                .contactEmail(client.getContactEmail())
                .status(client.getStatus())
                .credentials(credentials)
                .build();
    }

    @Transactional
    public ClientUpdateResponse updateClient(String clientId, ClientUpdateRequest request, Authentication authentication) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        Client client = clientRepository.findByIdWithAuthConfigs(clientId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_NOT_FOUND));
        String actor = actor(authentication);
        ClientStatus oldStatus = client.getStatus();
        List<String> changedFields = updateClientMetadata(client, request, actor);

        ClientAuthConfigChangesRequest changesRequest = request.getAuthConfigs();
        AuthChangeSet changeSet = applyAuthConfigChanges(client, changesRequest, actor);
        if (!changeSet.allIds().isEmpty()) {
            changedFields.add("authConfigs");
        }
        clientRepository.save(client);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("oldStatus", oldStatus);
        auditPayload.put("newStatus", client.getStatus());
        auditPayload.put("authConfigIds", changeSet.allIds());
        writeAudit(actor, "CLIENT_UPDATED", client.getId(), auditPayload);
        registerAfterCommit("CLIENT_UPDATED", client.getId(), changeSet.allIds(), changeSet.serviceIds(), changedFields);

        return ClientUpdateResponse.builder()
                .id(client.getId())
                .clientCode(client.getClientKey())
                .name(client.getName())
                .description(client.getDescription())
                .contactEmail(client.getContactEmail())
                .status(client.getStatus())
                .authConfigChanges(ClientAuthConfigChangesResponse.builder()
                        .created(changeSet.created())
                        .updated(changeSet.updated())
                        .removed(changeSet.removed())
                        .build())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    private AuthConfig createAuthConfig(Client client, ClientAuthConfigCreateRequest request, List<ClientCredentialResponse> credentials) {
        validateAuthConfigCreate(request);
        if (client.getStatus() == ClientStatus.REVOKED) {
            throw new AppException(ErrorCode.INVALID_CLIENT_STATUS_TRANSITION, "Cannot add auth config to revoked client");
        }
        AuthConfigScope scope = resolveAuthConfigScope(request);
        ensureNoEnabledConflict(client.getId(), scope.service().getId(), null);
        AuthType type = parseAuthType(request.getType());
        ClientCredentialService.CredentialMaterial credential = credentialService.getOrCreateCredential(client.getId(), type);
        ClientCredentialResponse oneTimeCredential = credentialService.toOneTimeResponse(type, credential);
        if (oneTimeCredential != null && credentials.stream().noneMatch(item -> item.getType().equals(oneTimeCredential.getType()))) {
            credentials.add(oneTimeCredential);
        }

        AuthConfig authConfig = new AuthConfig();
        authConfig.setId(UUID.randomUUID().toString());
        authConfig.setClient(client);
        authConfig.setService(scope.service());
        authConfig.setInboundEndpoint(scope.legacyInboundEndpoint());
        authConfig.setType(type);
        authConfig.setAlgorithm(type == AuthType.HMAC_SIGNATURE ? normalizeHmacAlgorithm(request.getAlgorithm()) : null);
        authConfig.setExpiresAt(request.getExpiresAt());
        authConfig.setEnabled(true);
        authConfig.setSecretRef(credential.secretRef());
        authConfig.setCredentialHash(credential.credentialHash());
        return authConfig;
    }

    private List<String> updateClientMetadata(Client client, ClientUpdateRequest request, String actor) {
        List<String> changedFields = new ArrayList<>();
        if (request.getName() != null) {
            requireNotBlank(request.getName(), "name is required");
            client.setName(request.getName().trim());
            changedFields.add("name");
        }
        if (request.getDescription() != null) {
            client.setDescription(normalizeBlank(request.getDescription()));
            changedFields.add("description");
        }
        if (request.getContactEmail() != null) {
            validateEmail(request.getContactEmail());
            client.setContactEmail(normalizeBlank(request.getContactEmail()));
            changedFields.add("contactEmail");
        }
        if (request.getStatus() != null) {
            ClientStatus newStatus = parseStatus(request.getStatus(), false);
            validateStatusTransition(client.getStatus(), newStatus);
            if (client.getStatus() != newStatus) {
                client.setStatus(newStatus);
                changedFields.add("status");
                if (newStatus == ClientStatus.REVOKED) {
                    client.setRevokedAt(LocalDateTime.now());
                    client.setRevokedBy(actor);
                }
            }
        }
        return changedFields;
    }

    private AuthChangeSet applyAuthConfigChanges(Client client, ClientAuthConfigChangesRequest request, String actor) {
        if (request == null) {
            return new AuthChangeSet(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        List<ClientAuthConfigChangeItemResponse> created = new ArrayList<>();
        List<ClientAuthConfigChangeItemResponse> updated = new ArrayList<>();
        List<ClientAuthConfigChangeItemResponse> removed = new ArrayList<>();
        List<ClientCredentialResponse> ignoredPlaintextCredentials = new ArrayList<>();
        ensureNoDuplicateServices(safeList(request.getAdd()));

        for (String authConfigId : safeList(request.getRemoveAuthConfigIds())) {
            AuthConfig authConfig = loadOwnedAuthConfig(client.getId(), authConfigId);
            removed.add(toChangeItem(authConfig));
            client.getAuthConfigs().remove(authConfig);
            authConfigRepository.delete(authConfig);
        }
        for (ClientAuthConfigUpdateRequest updateRequest : safeList(request.getUpdate())) {
            validateAuthConfigUpdate(updateRequest);
            AuthConfig authConfig = loadOwnedAuthConfig(client.getId(), updateRequest.getAuthConfigId());
            if (updateRequest.getEnabled() != null) {
                if (Boolean.TRUE.equals(updateRequest.getEnabled())) {
                    ensureNoEnabledConflict(client.getId(), resolveAuthConfigService(authConfig).getId(), authConfig.getId());
                    authConfig.setDisabledAt(null);
                    authConfig.setDisabledBy(null);
                } else {
                    authConfig.setDisabledAt(LocalDateTime.now());
                    authConfig.setDisabledBy(actor);
                }
                authConfig.setEnabled(updateRequest.getEnabled());
            }
            authConfig.setAlgorithm(authConfig.getType() == AuthType.HMAC_SIGNATURE ? normalizeHmacAlgorithm(updateRequest.getAlgorithm()) : null);
            authConfig.setExpiresAt(updateRequest.getExpiresAt());
            authConfigRepository.save(authConfig);
            updated.add(toChangeItem(authConfig));
        }
        for (ClientAuthConfigCreateRequest addRequest : safeList(request.getAdd())) {
            AuthConfig authConfig = createAuthConfig(client, addRequest, ignoredPlaintextCredentials);
            authConfigRepository.save(authConfig);
            created.add(toChangeItem(authConfig));
        }
        return new AuthChangeSet(created, updated, removed);
    }

    private AuthConfig loadOwnedAuthConfig(String clientId, String authConfigId) {
        requireNotBlank(authConfigId, "authConfigId is required");
        AuthConfig authConfig = authConfigRepository.findById(authConfigId)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_CONFIG_NOT_FOUND));
        if (!authConfig.getClient().getId().equals(clientId)) {
            throw new AppException(ErrorCode.AUTH_CONFIG_CONFLICT, "Auth config does not belong to client");
        }
        return authConfig;
    }

    private void ensureNoEnabledConflict(String clientId, String serviceId, String excludeId) {
        if (!authConfigRepository.findEnabledServiceConflicts(clientId, serviceId, excludeId).isEmpty()) {
            throw new AppException(ErrorCode.AUTH_CONFIG_CONFLICT);
        }
    }

    private void validateCreateRequest(ClientCreateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Request body is required");
        }
        requireNotBlank(request.getName(), "name is required");
        validateEmail(request.getContactEmail());
        ensureNoDuplicateServices(safeList(request.getAuthConfigs()));
    }

    private String generateUniqueClientCode() {
        for (int attempt = 0; attempt < CLIENT_CODE_MAX_ATTEMPTS; attempt++) {
            String clientCode = CLIENT_CODE_PREFIX + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, CLIENT_CODE_RANDOM_LENGTH)
                    .toUpperCase(Locale.ROOT);
            if (!clientRepository.existsByClientKey(clientCode)) {
                return clientCode;
            }
        }
        throw new AppException(ErrorCode.CLIENT_CODE_ALREADY_EXISTS, "Unable to generate unique clientCode");
    }

    private void validateAuthConfigCreate(ClientAuthConfigCreateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "authConfig is required");
        }
        if (isBlank(request.getServiceId()) && isBlank(request.getInboundEndpointId())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "serviceId is required");
        }
        AuthType type = parseAuthType(request.getType());
        if (type == AuthType.HMAC_SIGNATURE) {
            normalizeHmacAlgorithm(request.getAlgorithm());
        } else if (request.getAlgorithm() != null && !request.getAlgorithm().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "API_KEY must not include algorithm");
        }
    }

    private void validateAuthConfigUpdate(ClientAuthConfigUpdateRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "authConfig update is required");
        }
        requireNotBlank(request.getAuthConfigId(), "authConfigId is required");
    }

    private void ensureNoDuplicateServices(List<ClientAuthConfigCreateRequest> requests) {
        Set<String> serviceIds = new HashSet<>();
        for (ClientAuthConfigCreateRequest request : requests) {
            if (request == null) {
                continue;
            }
            AuthConfigScope scope = resolveAuthConfigScope(request);
            if (!serviceIds.add(scope.service().getId())) {
                throw new AppException(ErrorCode.AUTH_CONFIG_CONFLICT, "Duplicate serviceId in request");
            }
        }
    }

    private AuthConfigScope resolveAuthConfigScope(ClientAuthConfigCreateRequest request) {
        SecureService requestedService = null;
        InboundEndpoint legacyEndpoint = null;

        if (!isBlank(request.getServiceId())) {
            requestedService = serviceRepository.findById(request.getServiceId().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        }
        if (!isBlank(request.getInboundEndpointId())) {
            legacyEndpoint = inboundEndpointRepository.findById(request.getInboundEndpointId().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));
            SecureService derivedService = legacyEndpoint.getSecureService();
            if (derivedService == null) {
                throw new AppException(ErrorCode.SERVICE_NOT_FOUND, "Inbound endpoint has no owning service");
            }
            if (requestedService != null && !requestedService.getId().equals(derivedService.getId())) {
                throw new AppException(ErrorCode.INVALID_INPUT, "serviceId does not match inboundEndpointId owning service");
            }
            requestedService = derivedService;
        }

        if (requestedService == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "serviceId is required");
        }
        return new AuthConfigScope(requestedService, legacyEndpoint);
    }

    private SecureService resolveAuthConfigService(AuthConfig authConfig) {
        if (authConfig.getService() != null) {
            return authConfig.getService();
        }
        InboundEndpoint endpoint = authConfig.getInboundEndpoint();
        if (endpoint != null && endpoint.getSecureService() != null) {
            return endpoint.getSecureService();
        }
        throw new AppException(ErrorCode.SERVICE_NOT_FOUND, "Auth config has no service scope");
    }

    private void validateStatusTransition(ClientStatus current, ClientStatus requested) {
        if (current == ClientStatus.REVOKED && requested != ClientStatus.REVOKED) {
            throw new AppException(ErrorCode.INVALID_CLIENT_STATUS_TRANSITION);
        }
    }

    private String normalizeHmacAlgorithm(String algorithm) {
        requireNotBlank(algorithm, "algorithm is required for HMAC_SIGNATURE");
        String trimmedAlgorithm = algorithm.trim();
        if (!isSupportedHmacAlgorithm(trimmedAlgorithm)) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "algorithm must be HmacSHA256, HmacSHA384, HmacSHA512 or legacy HMAC_SHA256/HMAC_SHA384/HMAC_SHA512");
        }
        return trimmedAlgorithm;
    }

    private boolean isSupportedHmacAlgorithm(String algorithm) {
        return HMAC_SHA256.equals(algorithm)
                || HMAC_SHA256_JCA.equals(algorithm)
                || HMAC_SHA384.equals(algorithm)
                || HMAC_SHA384_JCA.equals(algorithm)
                || HMAC_SHA512.equals(algorithm)
                || HMAC_SHA512_JCA.equals(algorithm);
    }

    private ClientStatus parseStatus(String status, boolean defaultActive) {
        if (status == null || status.isBlank()) {
            return defaultActive ? ClientStatus.ACTIVE : null;
        }
        try {
            return ClientStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "status must be ACTIVE, INACTIVE, or REVOKED");
        }
    }

    private AuthType parseAuthType(String type) {
        requireNotBlank(type, "type is required");
        if ("HMAC".equals(type.trim())) {
            return AuthType.HMAC_SIGNATURE;
        }
        try {
            return AuthType.valueOf(type.trim());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "type must be API_KEY, HMAC, or HMAC_SIGNATURE");
        }
    }

    private void validateEmail(String email) {
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new AppException(ErrorCode.INVALID_INPUT, "contactEmail is invalid");
        }
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new AppException(ErrorCode.INVALID_INPUT, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toKeywordPattern(String value) {
        String normalizedValue = normalizeBlank(value);
        return normalizedValue == null ? null : "%" + normalizedValue.toLowerCase(Locale.ROOT) + "%";
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "system";
    }

    private void writeAudit(String actor, String action, String entityId, Map<String, Object> payload) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setEntityType("CLIENT");
        auditLog.setEntityId(entityId);
        try {
            auditLog.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            auditLog.setPayload("{}");
        }
        auditLogRepository.save(auditLog);
    }

    private void registerAfterCommit(String eventType, String clientId, List<String> authConfigIds, List<String> serviceIds, List<String> changedFields) {
        ClientSecurityConfigEvent event = ClientSecurityConfigEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .clientId(clientId)
                .authConfigIds(authConfigIds)
                .serviceIds(serviceIds)
                .changedFields(changedFields)
                .version(System.currentTimeMillis())
                .build();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(event);
            }
        });
    }

    private ClientListItemResponse toListItem(Client client) {
        return ClientListItemResponse.builder()
                .id(client.getId())
                .clientCode(client.getClientKey())
                .name(client.getName())
                .description(client.getDescription())
                .contactEmail(client.getContactEmail())
                .status(client.getStatus())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    private ClientDetailResponse toDetail(Client client) {
        return ClientDetailResponse.builder()
                .id(client.getId())
                .clientCode(client.getClientKey())
                .name(client.getName())
                .description(client.getDescription())
                .contactEmail(client.getContactEmail())
                .status(client.getStatus())
                .authConfigs(client.getAuthConfigs().stream().map(this::toAuthConfigResponse).toList())
                .build();
    }

    private ClientAuthConfigResponse toAuthConfigResponse(AuthConfig authConfig) {
        InboundEndpoint endpoint = authConfig.getInboundEndpoint();
        SecureService service = resolveAuthConfigService(authConfig);
        return ClientAuthConfigResponse.builder()
                .id(authConfig.getId())
                .serviceId(service.getId())
                .serviceName(service.getName())
                .type(authConfig.getType())
                .algorithm(authConfig.getAlgorithm())
                .enabled(authConfig.getEnabled())
                .expiresAt(authConfig.getExpiresAt())
                .createdAt(authConfig.getCreatedAt())
                .updatedAt(authConfig.getUpdatedAt())
                .disabledAt(authConfig.getDisabledAt())
                .build();
    }

    private ClientAuthConfigChangeItemResponse toChangeItem(AuthConfig authConfig) {
        SecureService service = resolveAuthConfigService(authConfig);
        InboundEndpoint endpoint = authConfig.getInboundEndpoint();
        return ClientAuthConfigChangeItemResponse.builder()
                .authConfigId(authConfig.getId())
                .serviceId(service.getId())
                .serviceName(service.getName())
                .inboundEndpointId(endpoint != null ? endpoint.getId() : null)
                .type(authConfig.getType())
                .enabled(authConfig.getEnabled())
                .secretRef(authConfig.getSecretRef())
                .expiresAt(authConfig.getExpiresAt())
                .disabledAt(authConfig.getDisabledAt())
                .build();
    }

    private record AuthConfigScope(SecureService service, InboundEndpoint legacyInboundEndpoint) {
    }

    private record AuthChangeSet(List<ClientAuthConfigChangeItemResponse> created,
                                 List<ClientAuthConfigChangeItemResponse> updated,
                                 List<ClientAuthConfigChangeItemResponse> removed) {
        List<String> allIds() {
            List<String> ids = new ArrayList<>();
            created.forEach(item -> ids.add(item.getAuthConfigId()));
            updated.forEach(item -> ids.add(item.getAuthConfigId()));
            removed.forEach(item -> ids.add(item.getAuthConfigId()));
            return ids;
        }

        List<String> serviceIds() {
            Set<String> ids = new HashSet<>();
            created.forEach(item -> ids.add(item.getServiceId()));
            updated.forEach(item -> ids.add(item.getServiceId()));
            removed.forEach(item -> ids.add(item.getServiceId()));
            return ids.stream().filter(id -> id != null && !id.isBlank()).toList();
        }
    }
}
