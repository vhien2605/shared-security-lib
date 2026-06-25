package vdt.mini.management_service.service.anomaly.runtime;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.IncidentStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ElasticsearchAnomalyIncidentStore {
    static final String INDEX_PATTERN = "security-anomalies-*";
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAnomalyIncidentStore.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchAnomalyIncidentStore(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public Optional<ActiveIncident> findLatestActive(AnomalyGroupKey key, AnomalyType anomalyType, Instant cutoff) {
        if (key == null || anomalyType == null || cutoff == null) {
            throw new IllegalArgumentException("key, anomalyType and cutoff are required");
        }

        List<Query> filters = new ArrayList<>();
        filters.add(term("serviceId", key.serviceId()));
        filters.add(term("endpointId", key.endpointId()));
        filters.add(term("flowType", key.flowType()));
        filters.add(term("anomalyType", anomalyType.name()));
        filters.add(Query.of(q -> q.terms(t -> t.field("status").terms(values -> values.value(List.of(
                FieldValue.of(IncidentStatus.OPEN.name()), FieldValue.of(IncidentStatus.ONGOING.name())))))));
        filters.add(Query.of(q -> q.range(r -> r.date(d -> d.field("lastSeenAt").gte(cutoff.toString())))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(b -> b.filter(filters))))
                .withPageable(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastSeenAt")))
                .build();

        try {
            return elasticsearchOperations.search(query, Map.class, IndexCoordinates.of(INDEX_PATTERN))
                    .getSearchHits()
                    .stream()
                    .findFirst()
                    .map(this::toActiveIncident);
        } catch (RuntimeException exception) {
            log.warn("Failed to find active anomaly incident in Elasticsearch for key={} type={}", key, anomalyType, exception);
            return Optional.empty();
        }
    }

    public boolean updateDuplicate(ActiveIncident incident,
                                   String severity,
                                   int riskScore,
                                   Map<String, Object> featureSnapshot,
                                   Instant now,
                                   int matchedCount) {
        if (incident == null || incident.incidentId() == null || incident.incidentId().isBlank() || now == null) {
            throw new IllegalArgumentException("incident with id and now are required");
        }

        String maxSeverity = maxSeverity(incident.maxSeverity(), severity);
        int maxRiskScore = Math.max(incident.maxRiskScore(), riskScore);
        Map<String, Object> latestFeatureSnapshot = new LinkedHashMap<>(featureSnapshot == null ? Map.of() : featureSnapshot);
        latestFeatureSnapshot.put("incidentDedup", Map.of(
                "shouldPublish", false,
                "incidentId", incident.incidentId(),
                "matchedCount", matchedCount,
                "updatedAt", now.toString()
        ));

        Document document = Document.create();
        document.put("incidentId", incident.incidentId());
        document.put("status", IncidentStatus.ONGOING.name());
        document.put("lastSeenAt", now.toString());
        document.put("matchedCount", matchedCount);
        document.put("maxRiskScore", maxRiskScore);
        document.put("riskScore", maxRiskScore);
        document.put("maxSeverity", maxSeverity);
        document.put("anomalyLevel", maxSeverity);
        document.put("latestFeatureSnapshot", latestFeatureSnapshot);
        document.put("featureSnapshot", latestFeatureSnapshot);

        try {
            UpdateQuery updateQuery = UpdateQuery.builder(incident.incidentId()).withDocument(document).build();
            elasticsearchOperations.update(updateQuery, IndexCoordinates.of(incident.indexName()));
            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to update duplicate anomaly incident in Elasticsearch incidentId={}", incident.incidentId(), exception);
            return false;
        }
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value == null ? "" : value))));
    }

    private ActiveIncident toActiveIncident(SearchHit<Map> hit) {
        Map<String, Object> source = hit.getContent();
        String incidentId = stringValue(source.get("incidentId"));
        if (incidentId == null || incidentId.isBlank()) {
            incidentId = hit.getId();
        }
        return new ActiveIncident(
                hit.getIndex(),
                incidentId,
                instantValue(source.get("firstSeenAt")).orElse(Instant.EPOCH),
                instantValue(source.get("lastSeenAt")).orElse(Instant.EPOCH),
                intValue(source.get("matchedCount"), 1),
                intValue(source.getOrDefault("maxRiskScore", source.get("riskScore")), 0),
                stringValue(source.getOrDefault("maxSeverity", source.get("anomalyLevel")))
        );
    }

    private Optional<Instant> instantValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.toString()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String maxSeverity(String current, String candidate) {
        return severityRank(candidate) > severityRank(current) ? candidate : current;
    }

    private int severityRank(String severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    public record ActiveIncident(String indexName,
                                 String incidentId,
                                 Instant firstSeenAt,
                                 Instant lastSeenAt,
                                 int matchedCount,
                                 int maxRiskScore,
                                 String maxSeverity) {
    }
}
