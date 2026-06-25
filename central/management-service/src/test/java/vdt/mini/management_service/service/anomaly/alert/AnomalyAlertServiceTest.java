package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.response.InAppNotificationResponse;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class AnomalyAlertServiceTest {
    @Test
    void dispatch_shouldAlwaysPersistBroadcastAndDelegateStrategies() {
        AnomalyNotificationService notificationService = mock(AnomalyNotificationService.class);
        NotificationWebSocketBroadcaster broadcaster = mock(NotificationWebSocketBroadcaster.class);
        EndpointAlertConfigResolver resolver = mock(EndpointAlertConfigResolver.class);
        AlertChannelParser parser = mock(AlertChannelParser.class);
        AlertChannelStrategy email = mock(AlertChannelStrategy.class);
        AlertChannelStrategy slack = mock(AlertChannelStrategy.class);
        AlertConfig config = new AlertConfig();
        var notification = new InAppNotificationResponse("n-1", "title", "content", "HIGH", "anom-1", "inc-1",
                "trace-1", "svc-1", "ep-1", false, LocalDateTime.now(), "ANOMALY_NOTIFICATION");
        when(notificationService.create(any())).thenReturn(notification);
        when(resolver.resolve("INBOUND_HTTP", "ep-1")).thenReturn(Optional.of(config));
        when(parser.emailEnabled(config)).thenReturn(true);

        new AnomalyAlertService(notificationService, broadcaster, resolver, parser, List.of(email, slack))
                .dispatch(AnomalyTestFixtures.anomalyEvent("inc-1", "HIGH"));

        verify(notificationService).create(any());
        verify(broadcaster).broadcast(notification);
        verify(email).send(argThat(AnomalyAlertContext::emailEnabled));
        verify(slack).send(any());
    }
}
