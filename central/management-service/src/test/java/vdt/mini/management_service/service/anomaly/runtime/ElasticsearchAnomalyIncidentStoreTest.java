package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ElasticsearchAnomalyIncidentStoreTest {
    @Test
    void findLatestActive_shouldQuerySecurityAnomalyIndexByDedupKeyAndCutoff() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked") SearchHits<Map> hits = mock(SearchHits.class);
        @SuppressWarnings("unchecked") SearchHit<Map> hit = mock(SearchHit.class);
        when(hit.getId()).thenReturn("inc-1");
        when(hit.getIndex()).thenReturn("security-anomalies-2026.06.23");
        when(hit.getContent()).thenReturn(Map.of(
                "incidentId", "inc-1",
                "firstSeenAt", "2026-06-23T00:00:00Z",
                "lastSeenAt", "2026-06-23T00:01:00Z",
                "matchedCount", 3,
                "maxRiskScore", 9,
                "maxSeverity", "HIGH"
        ));
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(operations.search(any(Query.class), eq(Map.class), any(IndexCoordinates.class))).thenReturn(hits);

        var result = new ElasticsearchAnomalyIncidentStore(operations)
                .findLatestActive(AnomalyTestFixtures.key(), AnomalyType.FAILURE_SPIKE, Instant.parse("2026-06-23T00:00:00Z"));

        assertThat(result).isPresent();
        assertThat(result.get().incidentId()).isEqualTo("inc-1");
        assertThat(result.get().matchedCount()).isEqualTo(3);
        verify(operations).search(any(Query.class), eq(Map.class), argThat(index -> index.getIndexNames()[0].equals("security-anomalies-*")));
    }

    @Test
    void updateDuplicate_shouldUpdateExistingDocumentWithLatestIncidentState() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        var incident = new ElasticsearchAnomalyIncidentStore.ActiveIncident(
                "security-anomalies-2026.06.23", "inc-1", Instant.EPOCH, Instant.EPOCH, 1, 7, "MEDIUM");

        boolean result = new ElasticsearchAnomalyIncidentStore(operations)
                .updateDuplicate(incident, "HIGH", 12, Map.of("sourceAlertSeverity", "HIGH"), Instant.parse("2026-06-23T00:01:00Z"), 2);

        assertThat(result).isTrue();
        verify(operations).update(argThat((UpdateQuery query) -> query.getId().equals("inc-1")
                        && query.getDocument().get("matchedCount").equals(2)
                        && query.getDocument().get("maxRiskScore").equals(12)
                        && query.getDocument().get("maxSeverity").equals("HIGH")
                        && query.getDocument().containsKey("featureSnapshot")),
                argThat(index -> index.getIndexNames()[0].equals("security-anomalies-2026.06.23")));
    }
}
