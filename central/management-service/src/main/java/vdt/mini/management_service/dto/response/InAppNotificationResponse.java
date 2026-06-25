package vdt.mini.management_service.dto.response;

import vdt.mini.management_service.entity.InAppNotification;

import java.time.LocalDateTime;

public record InAppNotificationResponse(String id,
                                        String title,
                                        String content,
                                        String severity,
                                        String anomalyId,
                                        String incidentId,
                                        String traceId,
                                        String serviceId,
                                        String endpointId,
                                        boolean read,
                                        LocalDateTime createdAt,
                                        String type) {
    public static InAppNotificationResponse from(InAppNotification notification) {
        return new InAppNotificationResponse(notification.getId(), notification.getTitle(), notification.getContent(),
                notification.getSeverity(), notification.getAnomalyId(), notification.getIncidentId(), notification.getTraceId(),
                notification.getServiceId(), notification.getEndpointId(), notification.getReadAt() != null,
                notification.getCreatedAt(), "ANOMALY_NOTIFICATION");
    }
}
