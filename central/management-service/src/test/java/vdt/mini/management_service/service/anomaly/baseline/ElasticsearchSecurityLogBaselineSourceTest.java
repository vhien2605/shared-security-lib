package vdt.mini.management_service.service.anomaly.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.entity.SecurityEventLog;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElasticsearchSecurityLogBaselineSourceTest {
    @Test
    void loadRecentLogsForService_shouldMapElasticsearchLogToMessage() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked") SearchHits<SecurityEventLog> hits = mock(SearchHits.class);
        @SuppressWarnings("unchecked") SearchHit<SecurityEventLog> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(log("svc-1"));
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(operations.search(any(Query.class), eq(SecurityEventLog.class))).thenReturn(hits);

        var result = new ElasticsearchSecurityLogBaselineSource(operations, AnomalyTestFixtures.properties()).loadRecentLogsForService("svc-1");

        assertEquals(1, result.size());
        assertEquals("svc-1", result.getFirst().getServiceId());
        assertEquals("OUTBOUND_MQ", result.getFirst().getFlowType());
    }

    @Test
    void loadRecentLogsForService_shouldKeepResultsWhenMoreThanPreviousMaxLimit() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked") SearchHits<SecurityEventLog> hits = mock(SearchHits.class);
        @SuppressWarnings("unchecked") SearchHit<SecurityEventLog> first = mock(SearchHit.class);
        @SuppressWarnings("unchecked") SearchHit<SecurityEventLog> second = mock(SearchHit.class);
        when(first.getContent()).thenReturn(log("svc-1"));
        when(second.getContent()).thenReturn(log("svc-1"));
        when(hits.getSearchHits()).thenReturn(List.of(first, second));
        when(operations.search(any(Query.class), eq(SecurityEventLog.class))).thenReturn(hits);

        assertEquals(2, new ElasticsearchSecurityLogBaselineSource(operations, AnomalyTestFixtures.properties()).loadRecentLogsForService("svc-1").size());
    }

    private SecurityEventLog log(String serviceId) {
        SecurityEventLog log = new SecurityEventLog();
        log.setTimestamp("2026-06-23T00:00:00Z");
        log.setServiceId(serviceId);
        log.setEndpointId("ep-1");
        log.setFlowType("OUTBOUND_MQ");
        log.setDurationMs(10L);
        return log;
    }
}
