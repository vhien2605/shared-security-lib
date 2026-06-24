package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.entity.InAppNotification;
import vdt.mini.management_service.repository.InAppNotificationRepository;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnomalyNotificationServiceTest {
    @Test
    void create_shouldPersistTraceIdInResponseMapping() {
        InAppNotificationRepository repository = mock(InAppNotificationRepository.class);
        when(repository.save(any(InAppNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnomalyNotificationService service = new AnomalyNotificationService(repository);

        var response = service.create(AnomalyTestFixtures.anomalyEvent("inc-1", "HIGH"));

        assertThat(response.traceId()).isEqualTo("trace-1");
        assertThat(response.anomalyId()).isEqualTo("anom-1");
        assertThat(response.read()).isFalse();
    }
}
