package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.stereotype.Component;
import vdt.mini.management_service.util.enums.AlertChannel;

@Component
public class SmsAlertChannelStrategy implements AlertChannelStrategy {
    @Override
    public AlertChannel channel() {
        return AlertChannel.SMS;
    }

    @Override
    public void send(AnomalyAlertContext context) {
    }
}
