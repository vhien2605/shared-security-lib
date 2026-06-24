package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.stereotype.Component;
import vdt.mini.management_service.util.enums.AlertSeverity;

@Component
public class AlertSeverityPolicy {
    public boolean allows(AlertSeverity threshold, String anomalySeverity) {
        if (threshold == null) threshold = AlertSeverity.WARNING;
        int severityRank = rank(anomalySeverity);
        return switch (threshold) {
            case INFO -> severityRank >= 1;
            case WARNING -> severityRank >= 2;
            case CRITICAL -> severityRank >= 4;
        };
    }

    private int rank(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }
}
