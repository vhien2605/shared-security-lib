package vdt.mini.management_service.service.anomaly.baseline;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.entity.SecurityEventLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticsearchSecurityLogBaselineSource {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSecurityLogBaselineSource.class);
    private final ElasticsearchOperations elasticsearchOperations;
    private final AnomalyDetectionProperties properties;

    public ElasticsearchSecurityLogBaselineSource(ElasticsearchOperations elasticsearchOperations, AnomalyDetectionProperties properties) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.properties = properties;
    }

    public List<SecurityLogEventMessage> loadRecentLogs() {
        return loadRecentLogsForService(null);
    }

    public List<SecurityLogEventMessage> loadRecentLogsForService(String serviceId) {
        int maxLogs = properties.getBaseline().getMaxLogsPerRun();
        Instant from = Instant.now().minus(java.time.Duration.ofDays(properties.getBaseline().getLookbackDays()));
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.range(r -> r.date(d -> d.field("timestamp").gte(from.toString())))));
        if (serviceId != null && !serviceId.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("serviceId").value(FieldValue.of(serviceId)))));
        }
        Query query = Query.of(q -> q.bool(b -> b.filter(filters)));
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(0, maxLogs + 1, Sort.by(Sort.Direction.ASC, "timestamp")))
                .build();
        List<SecurityEventLog> logs = elasticsearchOperations.search(nativeQuery, SecurityEventLog.class).getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        if (logs.size() > maxLogs) {
            log.warn("Baseline source exceeded max logs per run: maxLogs={}, serviceId={}", maxLogs, serviceId);
            return List.of();
        }
        return logs.stream().map(SecurityLogEventMessage::from).toList();
    }
}
