package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.stereotype.Component;
import vdt.mini.management_service.entity.AlertConfig;

@Component
public class AlertChannelParser {
    public boolean emailEnabled(AlertConfig config) {
        if (config == null || config.getChannels() == null) return false;
        return config.getChannels().stream()
                .anyMatch(channel -> channel != null && "EMAIL".equalsIgnoreCase(channel.trim()));
    }
}
