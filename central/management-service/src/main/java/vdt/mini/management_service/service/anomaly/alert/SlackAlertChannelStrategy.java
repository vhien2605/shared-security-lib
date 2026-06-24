package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.stereotype.Component;
import vdt.mini.management_service.util.enums.AlertChannel;

@Component
public class SlackAlertChannelStrategy implements AlertChannelStrategy {
    @Override
    public AlertChannel channel() {
        return AlertChannel.SLACK;
    }

    @Override
    public void send(AnomalyAlertContext context) {
    }
}
