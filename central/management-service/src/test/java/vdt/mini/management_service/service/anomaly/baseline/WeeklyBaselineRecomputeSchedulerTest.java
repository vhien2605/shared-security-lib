package vdt.mini.management_service.service.anomaly.baseline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class WeeklyBaselineRecomputeSchedulerTest {
    @Test
    void recomputeWeeklyBaselines_invokesBuildService() {
        BaselineBuildService buildService = mock(BaselineBuildService.class);

        new WeeklyBaselineRecomputeScheduler(buildService).recomputeWeeklyBaselines();

        verify(buildService).buildAllActiveGroups();
    }

    @Test
    void recomputeWeeklyBaselines_failureSafe() {
        BaselineBuildService buildService = mock(BaselineBuildService.class);
        doThrow(new IllegalStateException("boom")).when(buildService).buildAllActiveGroups();

        assertDoesNotThrow(() -> new WeeklyBaselineRecomputeScheduler(buildService).recomputeWeeklyBaselines());
    }
}
