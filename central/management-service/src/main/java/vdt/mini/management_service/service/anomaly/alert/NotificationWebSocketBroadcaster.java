package vdt.mini.management_service.service.anomaly.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.response.InAppNotificationResponse;

@Service
public class NotificationWebSocketBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketBroadcaster.class);
    private final NotificationWebSocketHandler handler;
    private final ObjectMapper objectMapper;
    private final AnomalyDetectionProperties properties;

    public NotificationWebSocketBroadcaster(NotificationWebSocketHandler handler, ObjectMapper objectMapper,
                                            AnomalyDetectionProperties properties) {
        this.handler = handler;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void broadcast(InAppNotificationResponse response) {
        if (!properties.getAlert().getWebsocket().isEnabled()) return;
        try {
            handler.broadcast(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize notification WebSocket payload id={}", response.id(), exception);
        }
    }
}
