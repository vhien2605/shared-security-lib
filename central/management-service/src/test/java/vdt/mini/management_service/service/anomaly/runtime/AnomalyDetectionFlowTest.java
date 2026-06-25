package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.baseline.BaselineQueryService;
import vdt.mini.management_service.service.anomaly.alert.AnomalyAlertService;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowStore;
import vdt.mini.management_service.service.anomaly.rule.BehavioralRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.CompositeRiskEvaluator;
import vdt.mini.management_service.service.anomaly.rule.CompositeRiskScorer;
import vdt.mini.management_service.service.anomaly.rule.HistoricalLogRuleEvaluator;
import vdt.mini.management_service.service.anomaly.stat.RobustZCalculator;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnomalyDetectionFlowTest {
    @Test
    void process_latencyOutlierWithBaseline_shouldPublishAndUpdateRollingAfterEvaluation() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 200);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.logBaseline(3)));
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(RollingWindowSnapshot.empty(Instant.EPOCH, Instant.EPOCH));

        service(baselineQuery, rollingStore, publisher).process(event);

        var order = inOrder(rollingStore, publisher);
        order.verify(rollingStore).snapshotBefore(eq(AnomalyTestFixtures.key()), any());
        order.verify(publisher).publish(any(AnomalyEvent.class));
        order.verify(rollingStore).add(eq(AnomalyTestFixtures.key()), any(RollingWindowEntry.class));
    }

    @Test
    void process_missingBaseline_shouldNotPublishButStillUpdateRolling() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(RollingWindowSnapshot.empty(Instant.EPOCH, Instant.EPOCH));

        service(baselineQuery, rollingStore, publisher).process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 1000));

        verifyNoInteractions(publisher);
        verify(rollingStore).add(eq(AnomalyTestFixtures.key()), any(RollingWindowEntry.class));
    }

    @Test
    void process_failureSpike_shouldPublishBehavioralAnomaly() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.behaviorBaseline(2)));
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 0, 0.5, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(snapshot);

        service(baselineQuery, rollingStore, publisher).process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100));

        verify(publisher).publish(argThat(event -> event.anomalyType() == vdt.mini.management_service.util.enums.AnomalyType.FAILURE_SPIKE));
    }

    @Test
    void process_failureSpikeWithDefaultRobustZEpsilon_shouldPublishBehavioralAnomaly() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        AnomalyDetectionProperties properties = AnomalyTestFixtures.properties();
        properties.getRobustZ().setEpsilon(1.0);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.behaviorBaseline(2)));
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 0, 0.5, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(snapshot);

        service(baselineQuery, rollingStore, publisher, properties).process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100));

        verify(publisher).publish(argThat(event -> event.anomalyType() == vdt.mini.management_service.util.enums.AnomalyType.FAILURE_SPIKE));
    }

    @Test
    void process_failureAndRetrySpikes_shouldPublishEachDifferentAnomalyType() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.behaviorBaseline(2)));
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 10,
                0.5, 0, 0.5, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(snapshot);

        service(baselineQuery, rollingStore, publisher).process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100));

        ArgumentCaptor<AnomalyEvent> events = ArgumentCaptor.forClass(AnomalyEvent.class);
        verify(publisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues().stream().map(AnomalyEvent::anomalyType).toList())
                .containsExactlyInAnyOrder(AnomalyType.FAILURE_SPIKE, AnomalyType.RETRY_SPIKE);
        AnomalyEvent failureEvent = events.getAllValues().stream().filter(e -> e.anomalyType() == AnomalyType.FAILURE_SPIKE).findFirst().orElseThrow();
        AnomalyEvent retryEvent = events.getAllValues().stream().filter(e -> e.anomalyType() == AnomalyType.RETRY_SPIKE).findFirst().orElseThrow();
        assertThat(failureEvent.matchedRules()).containsExactly("BEHAVIOR_FAILURE_001");
        assertThat(retryEvent.matchedRules()).containsExactly("BEHAVIOR_RETRY_001");
    }

    @Test
    void process_sourceSeverityOnly_shouldNotPublishAnomaly() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100);
        event.setAlertSeverity("CRITICAL");
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(RollingWindowSnapshot.empty(Instant.EPOCH, Instant.EPOCH));

        service(baselineQuery, rollingStore, publisher).process(event);

        verifyNoInteractions(publisher);
    }

    @Test
    void process_compositeUpstreamDuplicate_shouldSuppressSecondPublish() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        ElasticsearchAnomalyIncidentStore incidentStore = mock(ElasticsearchAnomalyIncidentStore.class);
        var existingIncident = new ElasticsearchAnomalyIncidentStore.ActiveIncident(
                "security-anomalies-2026.06.23", "inc-1", Instant.EPOCH, Instant.EPOCH, 1, 10, "HIGH");
        when(incidentStore.findLatestActive(eq(AnomalyTestFixtures.key()), eq(AnomalyType.UPSTREAM_DEGRADATION), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingIncident));
        when(incidentStore.updateDuplicate(eq(existingIncident), anyString(), anyInt(), any(Map.class), any(), eq(2))).thenReturn(true);
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.behaviorBaseline(2)));
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 10,
                0.5, 0, 0.5, null, null, 200.0, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(snapshot);

        AnomalyDetectionService service = service(baselineQuery, rollingStore, publisher, AnomalyTestFixtures.properties(), new IncidentDedupService(incidentStore, AnomalyTestFixtures.properties()));
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100);
        event.setStatus("FAILED");
        event.setRetryAttempt(1);
        service.process(event);
        service.process(event);

        verify(publisher, atLeastOnce()).publish(argThat(anomaly -> anomaly.anomalyType() == AnomalyType.UPSTREAM_DEGRADATION && anomaly.incidentId() != null && !anomaly.incidentId().isBlank()));
        verify(incidentStore).updateDuplicate(eq(existingIncident), anyString(), anyInt(), any(Map.class), any(), eq(2));
    }

    @Test
    void process_duplicateSuppressed_shouldDispatchAlertOnlyForPublishedAnomaly() {
        BaselineQueryService baselineQuery = mock(BaselineQueryService.class);
        RollingWindowStore rollingStore = mock(RollingWindowStore.class);
        AnomalyEventPublisher publisher = mock(AnomalyEventPublisher.class);
        AnomalyAlertService alertService = mock(AnomalyAlertService.class);
        IncidentDedupService dedupService = mock(IncidentDedupService.class);
        when(dedupService.deduplicate(any(), any(), anyString(), anyInt(), any(Map.class), any()))
                .thenAnswer(invocation -> new IncidentDedupResult(true, "inc-test", invocation.getArgument(5), invocation.getArgument(5), 1))
                .thenAnswer(invocation -> new IncidentDedupResult(false, "inc-test", invocation.getArgument(5), invocation.getArgument(5), 2));
        when(baselineQuery.findLogBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.of(AnomalyTestFixtures.logBaseline(3)));
        when(baselineQuery.findBehaviorBaseline(AnomalyTestFixtures.key())).thenReturn(Optional.empty());
        when(rollingStore.snapshotBefore(eq(AnomalyTestFixtures.key()), any())).thenReturn(RollingWindowSnapshot.empty(Instant.EPOCH, Instant.EPOCH));

        new AnomalyDetectionService(new SecurityLogValidator(), new StaticResultContextParser(), baselineQuery, rollingStore,
                new DeviationCalculatorService(new RobustZCalculator(AnomalyTestFixtures.properties()), AnomalyTestFixtures.properties()),
                new HistoricalLogRuleEvaluator(), new BehavioralRuleEvaluator(), new CompositeRiskScorer(), new CompositeRiskEvaluator(),
                new AnomalyTypeResolver(), new AnomalySeverityResolver(AnomalyTestFixtures.properties()), dedupService, publisher, alertService)
                .process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 200));
        new AnomalyDetectionService(new SecurityLogValidator(), new StaticResultContextParser(), baselineQuery, rollingStore,
                new DeviationCalculatorService(new RobustZCalculator(AnomalyTestFixtures.properties()), AnomalyTestFixtures.properties()),
                new HistoricalLogRuleEvaluator(), new BehavioralRuleEvaluator(), new CompositeRiskScorer(), new CompositeRiskEvaluator(),
                new AnomalyTypeResolver(), new AnomalySeverityResolver(AnomalyTestFixtures.properties()), dedupService, publisher, alertService)
                .process(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 200));

        verify(publisher, times(1)).publish(any(AnomalyEvent.class));
        verify(alertService, times(1)).dispatch(any(AnomalyEvent.class));
    }

    private AnomalyDetectionService service(BaselineQueryService baselineQuery, RollingWindowStore rollingStore, AnomalyEventPublisher publisher) {
        return service(baselineQuery, rollingStore, publisher, AnomalyTestFixtures.properties());
    }

    private AnomalyDetectionService service(BaselineQueryService baselineQuery, RollingWindowStore rollingStore, AnomalyEventPublisher publisher, AnomalyDetectionProperties properties) {
        IncidentDedupService dedupService = mock(IncidentDedupService.class);
        when(dedupService.deduplicate(any(), any(), anyString(), anyInt(), any(Map.class), any()))
                .thenAnswer(invocation -> new IncidentDedupResult(true, "inc-test", invocation.getArgument(5), invocation.getArgument(5), 1));
        return service(baselineQuery, rollingStore, publisher, properties, dedupService);
    }

    private AnomalyDetectionService service(BaselineQueryService baselineQuery, RollingWindowStore rollingStore, AnomalyEventPublisher publisher,
                                            AnomalyDetectionProperties properties, IncidentDedupService dedupService) {
        return new AnomalyDetectionService(new SecurityLogValidator(), new StaticResultContextParser(), baselineQuery, rollingStore,
                new DeviationCalculatorService(new RobustZCalculator(properties), properties), new HistoricalLogRuleEvaluator(), new BehavioralRuleEvaluator(),
                new CompositeRiskScorer(), new CompositeRiskEvaluator(), new AnomalyTypeResolver(), new AnomalySeverityResolver(properties), dedupService, publisher);
    }
}
