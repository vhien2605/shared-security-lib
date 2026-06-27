package vdt.mini.management_service.service.anomaly.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaselineActivationServiceTest {
    @Test
    void activate_requiresNewTransactionBecauseRegistrationBuildRunsAfterCommit() throws NoSuchMethodException {
        Method method = BaselineActivationService.class.getMethod("activate", LogBaselineSnapshot.class, BehaviorBaselineSnapshot.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
