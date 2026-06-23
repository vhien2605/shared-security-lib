package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.baseline.BaselineQueryService;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowStore;
import vdt.mini.management_service.service.anomaly.rule.BehavioralRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.HistoricalLogRuleEvaluator;
import vdt.mini.management_service.service.anomaly.stat.RobustZCalculator;

import java.time.Instant;
import java.util.Optional;

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

    private AnomalyDetectionService service(BaselineQueryService baselineQuery, RollingWindowStore rollingStore, AnomalyEventPublisher publisher) {
        return service(baselineQuery, rollingStore, publisher, AnomalyTestFixtures.properties());
    }

    private AnomalyDetectionService service(BaselineQueryService baselineQuery, RollingWindowStore rollingStore, AnomalyEventPublisher publisher, AnomalyDetectionProperties properties) {
        return new AnomalyDetectionService(new SecurityLogValidator(), new StaticResultContextParser(), baselineQuery, rollingStore,
                new DeviationCalculatorService(new RobustZCalculator(properties), properties), new HistoricalLogRuleEvaluator(), new BehavioralRuleEvaluator(), publisher);
    }
}
