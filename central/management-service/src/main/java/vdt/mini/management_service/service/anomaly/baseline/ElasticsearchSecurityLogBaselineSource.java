package vdt.mini.management_service.service.anomaly.baseline;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
    private static final int PAGE_SIZE = 1000;
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
        Instant from = Instant.now().minus(java.time.Duration.ofDays(properties.getBaseline().getLookbackDays()));
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.range(r -> r.date(d -> d.field("timestamp").gte(from.toString())))));
        if (serviceId != null && !serviceId.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("serviceId").value(FieldValue.of(serviceId)))));
        }
        Query query = Query.of(q -> q.bool(b -> b.filter(filters)));
        List<SecurityEventLog> logs = new ArrayList<>();
        int page = 0;
        while (true) {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "timestamp")))
                    .build();
            List<SecurityEventLog> pageLogs = elasticsearchOperations.search(nativeQuery, SecurityEventLog.class).getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .toList();
            logs.addAll(pageLogs);
            if (pageLogs.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return logs.stream().map(SecurityLogEventMessage::from).toList();
    }
}
