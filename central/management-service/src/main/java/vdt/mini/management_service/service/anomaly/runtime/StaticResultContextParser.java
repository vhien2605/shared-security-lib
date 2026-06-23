package vdt.mini.management_service.service.anomaly.runtime;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.dto.event.StaticResultContext;

import java.util.Locale;

@Service
public class StaticResultContextParser {
    public StaticResultContext parse(SecurityLogEventMessage event) {
        String status = normalize(event.getStatus());
        Integer retryAttempt = event.getRetryAttempt();
        return new StaticResultContext(
                status,
                event.getResultCode(),
                normalize(event.getErrorCode()),
                event.getDenyReason(),
                event.getRemainingQuota(),
                retryAttempt,
                event.getRollbackStrategy(),
                "FAILED".equals(status) || "ERROR".equals(status) || "TIMEOUT".equals(status),
                "DENIED".equals(status),
                retryAttempt != null && retryAttempt > 0);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
