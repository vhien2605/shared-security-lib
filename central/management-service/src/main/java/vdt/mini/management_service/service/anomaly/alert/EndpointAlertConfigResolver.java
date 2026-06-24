package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;

import java.util.Optional;

@Service
public class EndpointAlertConfigResolver {
    private final InboundEndpointRepository inboundEndpointRepository;
    private final OutboundEndpointRepository outboundEndpointRepository;

    public EndpointAlertConfigResolver(InboundEndpointRepository inboundEndpointRepository,
                                       OutboundEndpointRepository outboundEndpointRepository) {
        this.inboundEndpointRepository = inboundEndpointRepository;
        this.outboundEndpointRepository = outboundEndpointRepository;
    }

    public Optional<AlertConfig> resolve(String flowType, String endpointId) {
        if (endpointId == null || endpointId.isBlank()) return Optional.empty();
        if (flowType != null && flowType.toUpperCase().startsWith("INBOUND")) {
            return inboundEndpointRepository.findAnyByIdWithAlert(endpointId).map(endpoint -> endpoint.getAlertConfig());
        }
        return outboundEndpointRepository.findAnyByIdWithAlert(endpointId).map(endpoint -> endpoint.getAlertConfig());
    }
}
