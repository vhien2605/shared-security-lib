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

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfigQueryService {

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
                .status(service.getStatus())
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
                .status(service.getStatus())
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
}
