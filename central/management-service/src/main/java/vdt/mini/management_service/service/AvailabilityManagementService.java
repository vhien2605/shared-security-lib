package vdt.mini.management_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.EndpointStatus;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.util.enums.ServiceStatus;

@Service
@RequiredArgsConstructor
public class AvailabilityManagementService {

    private final ServiceRepository serviceRepository;
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;
    private final RedisSettingsSyncService redisSettingsSyncService;

    @Transactional
    public SecureService updateServiceStatus(String serviceId, ServiceStatus status) {
        if (status == null || status == ServiceStatus.DEPRECATED) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        SecureService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        service.setStatus(status);
        EndpointStatus endpointStatus = toEndpointStatus(status);
        syncEndpointStatuses(serviceId, endpointStatus);
        SecureService saved = serviceRepository.save(service);
        runAfterCommit(() -> redisSettingsSyncService.syncAllEndpointsOfService(saved.getId()));
        return saved;
    }

    @Transactional
    public InboundEndpoint updateInboundStatus(String endpointId, EndpointStatus status) {
        if (status == null) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        InboundEndpoint endpoint = inboundEndpointRepository.findAnyByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.INBOUND_ENDPOINT_NOT_FOUND));
        validateActivation(status, endpoint.getEnabled());
        endpoint.setStatus(status);
        InboundEndpoint saved = inboundEndpointRepository.save(endpoint);
        runAfterCommit(() -> redisSettingsSyncService.syncInboundToRedis(saved));
        return saved;
    }

    @Transactional
    public OutboundEndpoint updateOutboundStatus(String endpointId, EndpointStatus status) {
        if (status == null) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        OutboundEndpoint endpoint = outboundEndpointRepository.findAnyByIdWithAlert(endpointId)
                .orElseThrow(() -> new AppException(ErrorCode.OUTBOUND_ENDPOINT_NOT_FOUND));
        validateActivation(status, endpoint.getEnabled());
        endpoint.setStatus(status);
        OutboundEndpoint saved = outboundEndpointRepository.save(endpoint);
        runAfterCommit(() -> redisSettingsSyncService.syncOutboundToRedis(saved));
        return saved;
    }

    private void validateActivation(EndpointStatus status, Boolean enabled) {
        if (status == EndpointStatus.ACTIVE && !Boolean.TRUE.equals(enabled)) {
            throw new AppException(ErrorCode.ENDPOINT_NOT_DISCOVERED);
        }
    }

    private void syncEndpointStatuses(String serviceId, EndpointStatus status) {
        var inboundEndpoints = inboundEndpointRepository.findAllBySecureServiceId(serviceId);
        for (InboundEndpoint endpoint : inboundEndpoints) {
            endpoint.setStatus(status);
        }
        inboundEndpointRepository.saveAll(inboundEndpoints);

        var outboundEndpoints = outboundEndpointRepository.findAllBySecureServiceId(serviceId);
        for (OutboundEndpoint endpoint : outboundEndpoints) {
            endpoint.setStatus(status);
        }
        outboundEndpointRepository.saveAll(outboundEndpoints);
    }

    private EndpointStatus toEndpointStatus(ServiceStatus status) {
        return status == ServiceStatus.ACTIVE ? EndpointStatus.ACTIVE : EndpointStatus.INACTIVE;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }
}
