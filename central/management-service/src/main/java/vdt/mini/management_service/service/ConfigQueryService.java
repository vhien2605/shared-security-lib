package vdt.mini.management_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.dto.response.InboundEndpointResponse;
import vdt.mini.management_service.dto.response.OutboundEndpointResponse;
import vdt.mini.management_service.dto.response.ServiceDetailResponse;
import vdt.mini.management_service.dto.response.ServiceListResponse;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.EndpointStatus;
import vdt.mini.management_service.util.enums.ServiceStatus;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfigQueryService {
    private static final int DEFAULT_INBOUND_SEARCH_SIZE = 10;
    private static final int MAX_INBOUND_SEARCH_SIZE = 20;
    private static final int DEFAULT_SERVICE_SEARCH_SIZE = 10;
    private static final int MAX_SERVICE_SEARCH_SIZE = 20;

    private final ServiceRepository serviceRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;

    public Page<ServiceListResponse> getServices(Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "id"));
        Page<SecureService> page = serviceRepository.findAll(effectivePageable);
        List<String> ids = page.getContent().stream().map(SecureService::getId).toList();
        Map<String, Long> inboundCountMap = serviceRepository.countInboundsByServiceIds(ids);
        Map<String, Long> outboundCountMap = serviceRepository.countOutboundsByServiceIds(ids);
        return page.map(service -> ServiceListResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .baseUrl(service.getBaseUrl())
                .status(serviceStatus(service))
                .description(service.getDescription())
                .inboundCount(inboundCountMap.getOrDefault(service.getId(), 0L).intValue())
                .outboundCount(outboundCountMap.getOrDefault(service.getId(), 0L).intValue())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build());
    }

    public ServiceDetailResponse getServiceDetail(String serviceId) {
        SecureService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        Map<String, Long> inboundCountMap = serviceRepository.countInboundsByServiceIds(List.of(serviceId));
        Map<String, Long> outboundCountMap = serviceRepository.countOutboundsByServiceIds(List.of(serviceId));
        return ServiceDetailResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .baseUrl(service.getBaseUrl())
                .status(serviceStatus(service))
                .inboundCount(inboundCountMap.getOrDefault(serviceId, 0L).intValue())
                .outboundCount(outboundCountMap.getOrDefault(serviceId, 0L).intValue())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    public List<InboundEndpointResponse> getInboundsByService(String serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new AppException(ErrorCode.SERVICE_NOT_FOUND);
        }
        List<InboundEndpoint> endpoints = inboundEndpointRepository.findBySecureServiceIdWithAlert(serviceId);
        return endpoints.stream()
                .map(this::toInboundResponse)
                .toList();
    }

    public List<InboundEndpointResponse> searchInboundsByName(String name, Integer size) {
        int safeSize = size == null ? DEFAULT_INBOUND_SEARCH_SIZE : Math.min(Math.max(size, 1), MAX_INBOUND_SEARCH_SIZE);
        String namePattern = toNamePattern(name);
        return inboundEndpointRepository.searchEnabledByName(namePattern, PageRequest.of(0, safeSize))
                .stream()
                .map(this::toInboundResponse)
                .toList();
    }

    public List<ServiceListResponse> searchServicesByName(String name, Integer size) {
        int safeSize = size == null ? DEFAULT_SERVICE_SEARCH_SIZE : Math.min(Math.max(size, 1), MAX_SERVICE_SEARCH_SIZE);
        List<SecureService> services = serviceRepository.searchByName(toNamePattern(name), PageRequest.of(0, safeSize));
        List<String> ids = services.stream().map(SecureService::getId).toList();
        Map<String, Long> inboundCountMap = ids.isEmpty() ? Collections.emptyMap() : serviceRepository.countInboundsByServiceIds(ids);
        Map<String, Long> outboundCountMap = ids.isEmpty() ? Collections.emptyMap() : serviceRepository.countOutboundsByServiceIds(ids);
        return services.stream()
                .map(service -> toServiceListResponse(service, inboundCountMap, outboundCountMap))
                .toList();
    }

    public List<OutboundEndpointResponse> getOutboundsByService(String serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new AppException(ErrorCode.SERVICE_NOT_FOUND);
        }
        List<OutboundEndpoint> endpoints = outboundEndpointRepository.findBySecureServiceIdWithAlert(serviceId);
        return endpoints.stream()
                .map(this::toOutboundResponse)
                .toList();
    }

    public InboundEndpointResponse getInboundDetail(String endpointId) {
        InboundEndpoint endpoint = inboundEndpointRepository.findByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));
        return toInboundResponse(endpoint);
    }

    public OutboundEndpointResponse getOutboundDetail(String endpointId) {
        OutboundEndpoint endpoint = outboundEndpointRepository.findByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.OUTBOUND_ENDPOINT_NOT_FOUND));
        return toOutboundResponse(endpoint);
    }

    private InboundEndpointResponse toInboundResponse(InboundEndpoint ep) {
        return InboundEndpointResponse.builder()
                .id(ep.getId())
                .serviceId(ep.getSecureService() != null ? ep.getSecureService().getId() : null)
                .name(ep.getName())
                .path(ep.getPath())
                .topic(ep.getTopic())
                .method(ep.getMethod())
                .protocol(ep.getProtocol())
                .enabled(ep.getEnabled())
                .status(endpointStatus(ep.getStatus()))
                .available(isInboundAvailable(ep))
                .serviceStatus(serviceStatus(ep.getSecureService()))
                .rateLimit(ep.getRateLimit())
                .rateLimitWindowSeconds(ep.getRateLimitWindowSeconds())
                .timeoutMs(ep.getTimeoutMs())
                .requestSizeLimitKb(ep.getRequestSizeLimitKb())
                .responseSizeLimitKb(ep.getResponseSizeLimitKb())
                .responseTimeThresholdMs(ep.getResponseTimeThresholdMs())
                .logRetentionDays(ep.getLogRetentionDays())
                .alertSeverity(ep.getAlertConfig() != null && ep.getAlertConfig().getSeverity() != null
                        ? ep.getAlertConfig().getSeverity().name() : null)
                .alertThrottleMinutes(ep.getAlertConfig() != null ? ep.getAlertConfig().getThrottleMinutes() : null)
                .alertChannels(ep.getAlertConfig() != null ? ep.getAlertConfig().getChannels() : null)
                .createdAt(ep.getCreatedAt())
                .updatedAt(ep.getUpdatedAt())
                .build();
    }

    private OutboundEndpointResponse toOutboundResponse(OutboundEndpoint ep) {
        return OutboundEndpointResponse.builder()
                .id(ep.getId())
                .serviceId(ep.getSecureService() != null ? ep.getSecureService().getId() : null)
                .name(ep.getName())
                .targetUrl(ep.getTargetUrl())
                .topic(ep.getTopic())
                .method(ep.getMethod())
                .protocol(ep.getProtocol())
                .enabled(ep.getEnabled())
                .status(endpointStatus(ep.getStatus()))
                .available(isOutboundAvailable(ep))
                .serviceStatus(serviceStatus(ep.getSecureService()))
                .timeoutMs(ep.getTimeoutMs())
                .retryCount(ep.getRetryCount())
                .retryBackoffMs(ep.getRetryBackoffMs())
                .responseTimeThresholdMs(ep.getResponseTimeThresholdMs())
                .logRetentionDays(ep.getLogRetentionDays())
                .rollbackStrategy(ep.getRollbackStrategy())
                .alertSeverity(ep.getAlertConfig() != null && ep.getAlertConfig().getSeverity() != null
                        ? ep.getAlertConfig().getSeverity().name() : null)
                .alertThrottleMinutes(ep.getAlertConfig() != null ? ep.getAlertConfig().getThrottleMinutes() : null)
                .alertChannels(ep.getAlertConfig() != null ? ep.getAlertConfig().getChannels() : null)
                .createdAt(ep.getCreatedAt())
                .updatedAt(ep.getUpdatedAt())
                .build();
    }

    private ServiceListResponse toServiceListResponse(SecureService service,
                                                      Map<String, Long> inboundCountMap,
                                                      Map<String, Long> outboundCountMap) {
        return ServiceListResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .baseUrl(service.getBaseUrl())
                .status(serviceStatus(service))
                .description(service.getDescription())
                .inboundCount(inboundCountMap.getOrDefault(service.getId(), 0L).intValue())
                .outboundCount(outboundCountMap.getOrDefault(service.getId(), 0L).intValue())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    private boolean isInboundAvailable(InboundEndpoint endpoint) {
        return isAvailable(endpoint.getSecureService(), endpoint.getEnabled(), endpoint.getStatus());
    }

    private boolean isOutboundAvailable(OutboundEndpoint endpoint) {
        return isAvailable(endpoint.getSecureService(), endpoint.getEnabled(), endpoint.getStatus());
    }

    private boolean isAvailable(SecureService service, Boolean enabled, EndpointStatus endpointStatus) {
        return serviceStatus(service) == ServiceStatus.ACTIVE
                && Boolean.TRUE.equals(enabled)
                && endpointStatus(endpointStatus) == EndpointStatus.ACTIVE;
    }

    private ServiceStatus serviceStatus(SecureService service) {
        return service != null && service.getStatus() != null ? service.getStatus() : ServiceStatus.INACTIVE;
    }

    private EndpointStatus endpointStatus(EndpointStatus status) {
        return status != null ? status : EndpointStatus.ACTIVE;
    }

    private String toNamePattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
