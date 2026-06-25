package vdt.mini.management_service.dto.event;

import java.time.Instant;

public record IncidentDedupResult(boolean shouldPublish,
                                  String incidentId,
                                  Instant firstSeenAt,
                                  Instant lastSeenAt,
                                  int matchedCount) {
    public static IncidentDedupResult publishWithoutIncident(Instant now, int matchedCount) {
        return new IncidentDedupResult(true, null, now, now, matchedCount);
    }
}
