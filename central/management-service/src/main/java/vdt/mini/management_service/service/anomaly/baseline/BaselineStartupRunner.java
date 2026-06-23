package vdt.mini.management_service.service.anomaly.baseline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "anomaly", name = "enabled", havingValue = "true")
public class BaselineStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BaselineStartupRunner.class);
    private final BaselineBuildService baselineBuildService;

    public BaselineStartupRunner(BaselineBuildService baselineBuildService) {
        this.baselineBuildService = baselineBuildService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            baselineBuildService.buildAllActiveGroups();
        } catch (RuntimeException exception) {
            log.warn("Startup baseline build failed; anomaly detection will run in degraded mode until baselines are available", exception);
        }
    }
}
