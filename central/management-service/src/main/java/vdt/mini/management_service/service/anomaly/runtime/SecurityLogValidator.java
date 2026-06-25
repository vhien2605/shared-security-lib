package vdt.mini.management_service.service.anomaly.runtime;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Service
public class SecurityLogValidator {
    private static final Set<String> FLOW_TYPES = Set.of("INBOUND_HTTP", "INBOUND_MQ_LISTENER", "OUTBOUND_HTTP", "OUTBOUND_MQ");

    public boolean isValid(SecurityLogEventMessage event) {
        if (event == null || !hasText(event.getServiceId()) || !hasText(event.getEndpointId()) || !hasText(event.getFlowType())) {
            return false;
        }
        if (!FLOW_TYPES.contains(event.getFlowType())) {
            return false;
        }
        try {
            Instant.parse(event.getTimestamp());
            return true;
        } catch (DateTimeParseException | NullPointerException ex) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
