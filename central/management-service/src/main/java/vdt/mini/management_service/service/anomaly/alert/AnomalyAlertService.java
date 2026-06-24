package vdt.mini.management_service.service.anomaly.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.AnomalyEvent;
import vdt.mini.management_service.dto.response.InAppNotificationResponse;
import vdt.mini.management_service.entity.AlertConfig;

import java.util.List;

@Service
public class AnomalyAlertService {
    private static final Logger log = LoggerFactory.getLogger(AnomalyAlertService.class);
    private final AnomalyNotificationService notificationService;
    private final NotificationWebSocketBroadcaster webSocketBroadcaster;
    private final EndpointAlertConfigResolver configResolver;
    private final AlertChannelParser channelParser;
    private final List<AlertChannelStrategy> strategies;

    public AnomalyAlertService(AnomalyNotificationService notificationService,
                               NotificationWebSocketBroadcaster webSocketBroadcaster,
                               EndpointAlertConfigResolver configResolver,
                               AlertChannelParser channelParser,
                               List<AlertChannelStrategy> strategies) {
        this.notificationService = notificationService;
        this.webSocketBroadcaster = webSocketBroadcaster;
        this.configResolver = configResolver;
        this.channelParser = channelParser;
        this.strategies = strategies;
    }

    public void dispatch(AnomalyEvent event) {
        try {
            InAppNotificationResponse notification = notificationService.create(event);
            webSocketBroadcaster.broadcast(notification);
            AlertConfig config = configResolver.resolve(event.flowType(), event.endpointId()).orElse(null);
            AnomalyAlertContext context = new AnomalyAlertContext(event, notification.title(), notification.content(),
                    config, channelParser.emailEnabled(config));
            for (AlertChannelStrategy strategy : strategies) {
                strategy.send(context);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to dispatch anomaly alert anomalyId={}", event.anomalyId(), exception);
        }
    }
}
