package vdt.mini.management_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "anomaly")
public class AnomalyDetectionProperties {
    private boolean enabled = false;
    private Kafka kafka = new Kafka();
    private Baseline baseline = new Baseline();
    private Rolling rolling = new Rolling();
    private RobustZ robustZ = new RobustZ();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Baseline getBaseline() { return baseline; }
    public void setBaseline(Baseline baseline) { this.baseline = baseline; }
    public Rolling getRolling() { return rolling; }
    public void setRolling(Rolling rolling) { this.rolling = rolling; }
    public RobustZ getRobustZ() { return robustZ; }
    public void setRobustZ(RobustZ robustZ) { this.robustZ = robustZ; }

    public static class Kafka {
        private String logsTopic = "security.logs";
        private String anomaliesTopic = "security.anomalies";
        public String getLogsTopic() { return logsTopic; }
        public void setLogsTopic(String logsTopic) { this.logsTopic = logsTopic; }
        public String getAnomaliesTopic() { return anomaliesTopic; }
        public void setAnomaliesTopic(String anomaliesTopic) { this.anomaliesTopic = anomaliesTopic; }
    }

    public static class Baseline {
        private int lookbackDays = 14;
        private long minLogSamples = 500;
        private long minBehaviorWindows = 100;
        private int maxLogsPerRun = 10000;
        private int knownValueLimit = 200;
        public int getLookbackDays() { return lookbackDays; }
        public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
        public long getMinLogSamples() { return minLogSamples; }
        public void setMinLogSamples(long minLogSamples) { this.minLogSamples = minLogSamples; }
        public long getMinBehaviorWindows() { return minBehaviorWindows; }
        public void setMinBehaviorWindows(long minBehaviorWindows) { this.minBehaviorWindows = minBehaviorWindows; }
        public int getMaxLogsPerRun() { return maxLogsPerRun; }
        public void setMaxLogsPerRun(int maxLogsPerRun) { this.maxLogsPerRun = maxLogsPerRun; }
        public int getKnownValueLimit() { return knownValueLimit; }
        public void setKnownValueLimit(int knownValueLimit) { this.knownValueLimit = knownValueLimit; }
    }

    public static class Rolling {
        private Duration windowSize = Duration.ofMinutes(5);
        private Duration lateTolerance = Duration.ofMinutes(1);
        private int minSamples = 20;
        public Duration getWindowSize() { return windowSize; }
        public void setWindowSize(Duration windowSize) { this.windowSize = windowSize; }
        public Duration getLateTolerance() { return lateTolerance; }
        public void setLateTolerance(Duration lateTolerance) { this.lateTolerance = lateTolerance; }
        public int getMinSamples() { return minSamples; }
        public void setMinSamples(int minSamples) { this.minSamples = minSamples; }
    }

    public static class RobustZ {
        private double epsilon = 1.0;
        public double getEpsilon() { return epsilon; }
        public void setEpsilon(double epsilon) { this.epsilon = epsilon; }
    }
}
