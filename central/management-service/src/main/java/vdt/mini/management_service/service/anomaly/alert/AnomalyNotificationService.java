package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.dto.event.AnomalyEvent;
import vdt.mini.management_service.dto.response.InAppNotificationResponse;
import vdt.mini.management_service.entity.InAppNotification;
import vdt.mini.management_service.repository.InAppNotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AnomalyNotificationService {
    private final InAppNotificationRepository repository;

    public AnomalyNotificationService(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public InAppNotificationResponse create(AnomalyEvent event) {
        InAppNotification notification = new InAppNotification();
        notification.setId(UUID.randomUUID().toString());
        notification.setTitle(event.anomalyLevel() + " anomaly: " + event.anomalyType());
        notification.setContent(buildContent(event));
        notification.setSeverity(event.anomalyLevel());
        notification.setAnomalyId(event.anomalyId());
        notification.setIncidentId(event.incidentId());
        notification.setTraceId(event.traceId());
        notification.setServiceId(event.serviceId());
        notification.setEndpointId(event.endpointId());
        return InAppNotificationResponse.from(repository.save(notification));
    }

    @Transactional(readOnly = true)
    public List<InAppNotificationResponse> latest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(InAppNotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadAtIsNull();
    }

    @Transactional
    public InAppNotificationResponse markRead(String id) {
        InAppNotification notification = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        return InAppNotificationResponse.from(notification);
    }

    private String buildContent(AnomalyEvent event) {
        return String.format("%s/%s detected %s, riskScore=%d, incident=%s, traceId=%s",
                value(event.serviceId()), value(event.endpointId()), event.anomalyType(), event.riskScore(),
                value(event.incidentId()), value(event.traceId()));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
