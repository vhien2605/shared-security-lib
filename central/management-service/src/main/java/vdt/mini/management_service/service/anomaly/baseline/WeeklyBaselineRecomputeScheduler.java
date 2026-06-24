package vdt.mini.management_service.service.anomaly.baseline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "anomaly.baseline.recompute", name = "enabled", havingValue = "true")
public class WeeklyBaselineRecomputeScheduler {
    private static final Logger log = LoggerFactory.getLogger(WeeklyBaselineRecomputeScheduler.class);
    private final BaselineBuildService baselineBuildService;

    public WeeklyBaselineRecomputeScheduler(BaselineBuildService baselineBuildService) {
        this.baselineBuildService = baselineBuildService;
    }

    @Scheduled(cron = "${anomaly.baseline.recompute.cron:0 0 3 ? * SUN}", zone = "${anomaly.baseline.recompute.zone:UTC}")
    public void recomputeWeeklyBaselines() {
        try {
            log.info("Starting scheduled anomaly baseline recompute");
            baselineBuildService.buildAllActiveGroups();
            log.info("Finished scheduled anomaly baseline recompute");
        } catch (RuntimeException exception) {
            log.warn("Scheduled anomaly baseline recompute failed; keeping active baselines", exception);
        }
    }
}
