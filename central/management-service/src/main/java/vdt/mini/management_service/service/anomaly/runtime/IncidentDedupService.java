package vdt.mini.management_service.service.anomaly.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyContext;
import vdt.mini.management_service.dto.event.IncidentDedupResult;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class IncidentDedupService {
    private static final Logger log = LoggerFactory.getLogger(IncidentDedupService.class);
    private final ElasticsearchAnomalyIncidentStore incidentStore;
    private final AnomalyDetectionProperties properties;

    public IncidentDedupService(ElasticsearchAnomalyIncidentStore incidentStore, AnomalyDetectionProperties properties) {
        this.incidentStore = incidentStore;
        this.properties = properties;
    }

    public IncidentDedupResult deduplicate(AnomalyContext context, AnomalyType anomalyType, String severity, int riskScore, Map<String, Object> featureSnapshot, Instant now) {
        if (!properties.getIncident().isEnabled()) {
            return IncidentDedupResult.publishWithoutIncident(now, 1);
        }
        if (context == null || context.groupKey() == null || anomalyType == null || now == null) {
            throw new IllegalArgumentException("context, anomalyType and now are required for incident deduplication");
        }
        Instant cutoff = now.minus(properties.getIncident().getDedupWindow());
        try {
            return incidentStore.findLatestActive(context.groupKey(), anomalyType, cutoff)
                    .map(existing -> updateExisting(existing, severity, riskScore, featureSnapshot, now))
                    .orElseGet(() -> createNew(now));
        } catch (RuntimeException exception) {
            log.warn("Incident lookup failed for key={} type={}; publishing anomaly without dedup suppression", context.groupKey(), anomalyType, exception);
            return createNew(now);
        }
    }

    private IncidentDedupResult updateExisting(ElasticsearchAnomalyIncidentStore.ActiveIncident incident,
                                               String severity,
                                               int riskScore,
                                               Map<String, Object> featureSnapshot,
                                               Instant now) {
        int matchedCount = Math.max(incident.matchedCount(), 0) + 1;
        boolean updated = incidentStore.updateDuplicate(incident, severity, riskScore, featureSnapshot, now, matchedCount);
        if (!updated) {
            log.warn("Incident update failed for incidentId={}; publishing anomaly to preserve visibility", incident.incidentId());
            return new IncidentDedupResult(true, incident.incidentId(), incident.firstSeenAt(), now, matchedCount);
        }
        return new IncidentDedupResult(false, incident.incidentId(), incident.firstSeenAt(), now, matchedCount);
    }

    private IncidentDedupResult createNew(Instant now) {
        return new IncidentDedupResult(true, UUID.randomUUID().toString(), now, now, 1);
    }
}
