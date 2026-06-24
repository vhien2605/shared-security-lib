package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.IncidentDedupResult;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentDedupServiceTest {
    @Test
    void deduplicate_existingInsideWindow_shouldSuppressPublishAndUpdateElasticsearchIncident() {
        ElasticsearchAnomalyIncidentStore incidentStore = mock(ElasticsearchAnomalyIncidentStore.class);
        var properties = AnomalyTestFixtures.properties();
        Instant firstSeen = Instant.parse("2026-06-23T00:00:00Z");
        ElasticsearchAnomalyIncidentStore.ActiveIncident incident = incident(firstSeen);
        when(incidentStore.findLatestActive(eq(AnomalyTestFixtures.key()), eq(AnomalyType.FAILURE_SPIKE), any()))
                .thenReturn(Optional.of(incident));
        when(incidentStore.updateDuplicate(eq(incident), eq("HIGH"), eq(12), any(Map.class), any(), eq(2)))
                .thenReturn(true);

        IncidentDedupResult result = new IncidentDedupService(incidentStore, properties)
                .deduplicate(AnomalyTestFixtures.context(), AnomalyType.FAILURE_SPIKE, "HIGH", 12, Map.of("a", 1), firstSeen.plusSeconds(60));

        assertThat(result.shouldPublish()).isFalse();
        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.incidentId()).isEqualTo("inc-1");
        verify(incidentStore).updateDuplicate(eq(incident), eq("HIGH"), eq(12), any(Map.class), eq(firstSeen.plusSeconds(60)), eq(2));
    }

    @Test
    void deduplicate_noExistingIncident_shouldPublishNewIncident() {
        ElasticsearchAnomalyIncidentStore incidentStore = mock(ElasticsearchAnomalyIncidentStore.class);
        when(incidentStore.findLatestActive(eq(AnomalyTestFixtures.key()), eq(AnomalyType.FAILURE_SPIKE), any()))
                .thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-06-23T00:00:00Z");

        IncidentDedupResult result = new IncidentDedupService(incidentStore, AnomalyTestFixtures.properties())
                .deduplicate(AnomalyTestFixtures.context(), AnomalyType.FAILURE_SPIKE, "HIGH", 10, Map.of(), now);

        assertThat(result.shouldPublish()).isTrue();
        assertThat(result.incidentId()).isNotBlank();
        assertThat(result.matchedCount()).isEqualTo(1);
        verify(incidentStore, never()).updateDuplicate(any(), anyString(), anyInt(), any(), any(), anyInt());
    }

    @Test
    void deduplicate_updateFailure_shouldFailOpenAndPublishForVisibility() {
        ElasticsearchAnomalyIncidentStore incidentStore = mock(ElasticsearchAnomalyIncidentStore.class);
        Instant firstSeen = Instant.parse("2026-06-23T00:00:00Z");
        ElasticsearchAnomalyIncidentStore.ActiveIncident incident = incident(firstSeen);
        when(incidentStore.findLatestActive(eq(AnomalyTestFixtures.key()), eq(AnomalyType.FAILURE_SPIKE), any()))
                .thenReturn(Optional.of(incident));
        when(incidentStore.updateDuplicate(eq(incident), anyString(), anyInt(), any(Map.class), any(), eq(2)))
                .thenReturn(false);

        IncidentDedupResult result = new IncidentDedupService(incidentStore, AnomalyTestFixtures.properties())
                .deduplicate(AnomalyTestFixtures.context(), AnomalyType.FAILURE_SPIKE, "HIGH", 10, Map.of(), firstSeen.plusSeconds(60));

        assertThat(result.shouldPublish()).isTrue();
        assertThat(result.incidentId()).isEqualTo("inc-1");
        assertThat(result.matchedCount()).isEqualTo(2);
    }

    private ElasticsearchAnomalyIncidentStore.ActiveIncident incident(Instant firstSeen) {
        return new ElasticsearchAnomalyIncidentStore.ActiveIncident("security-anomalies-2026.06.23", "inc-1", firstSeen, firstSeen, 1, 5, "MEDIUM");
    }
}
