package vdt.mini.management_service.service.anomaly.alert;

import vdt.mini.management_service.util.enums.AlertChannel;

public interface AlertChannelStrategy {
    AlertChannel channel();

    void send(AnomalyAlertContext context);
}
