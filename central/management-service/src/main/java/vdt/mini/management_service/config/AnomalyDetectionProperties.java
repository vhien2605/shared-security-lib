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
    private Risk risk = new Risk();
    private Incident incident = new Incident();

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
    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

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
        private Duration behaviorSampleInterval = Duration.ofSeconds(30);
        private int knownValueLimit = 200;
        private Recompute recompute = new Recompute();
        public int getLookbackDays() { return lookbackDays; }
        public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
        public long getMinLogSamples() { return minLogSamples; }
        public void setMinLogSamples(long minLogSamples) { this.minLogSamples = minLogSamples; }
        public long getMinBehaviorWindows() { return minBehaviorWindows; }
        public void setMinBehaviorWindows(long minBehaviorWindows) { this.minBehaviorWindows = minBehaviorWindows; }
        public Duration getBehaviorSampleInterval() { return behaviorSampleInterval; }
        public void setBehaviorSampleInterval(Duration behaviorSampleInterval) {
            if (behaviorSampleInterval == null || behaviorSampleInterval.isZero() || behaviorSampleInterval.isNegative()) {
                throw new IllegalArgumentException("behaviorSampleInterval must be positive");
            }
            this.behaviorSampleInterval = behaviorSampleInterval;
        }
        public int getKnownValueLimit() { return knownValueLimit; }
        public void setKnownValueLimit(int knownValueLimit) { this.knownValueLimit = knownValueLimit; }
        public Recompute getRecompute() { return recompute; }
        public void setRecompute(Recompute recompute) { this.recompute = recompute; }

        public static class Recompute {
            private boolean enabled = false;
            private String cron = "0 0 3 ? * SUN";
            private String zone = "UTC";
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getCron() { return cron; }
            public void setCron(String cron) { this.cron = cron; }
            public String getZone() { return zone; }
            public void setZone(String zone) { this.zone = zone; }
        }
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

    public static class Risk {
        private int mediumSeverityThreshold = 6;
        private int highSeverityThreshold = 11;
        private int criticalSeverityThreshold = 16;
        public int getMediumSeverityThreshold() { return mediumSeverityThreshold; }
        public void setMediumSeverityThreshold(int mediumSeverityThreshold) { this.mediumSeverityThreshold = mediumSeverityThreshold; }
        public int getHighSeverityThreshold() { return highSeverityThreshold; }
        public void setHighSeverityThreshold(int highSeverityThreshold) { this.highSeverityThreshold = highSeverityThreshold; }
        public int getCriticalSeverityThreshold() { return criticalSeverityThreshold; }
        public void setCriticalSeverityThreshold(int criticalSeverityThreshold) { this.criticalSeverityThreshold = criticalSeverityThreshold; }
    }

    public static class Incident {
        private boolean enabled = true;
        private Duration dedupWindow = Duration.ofMinutes(5);
        private int retentionDays = 30;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getDedupWindow() { return dedupWindow; }
        public void setDedupWindow(Duration dedupWindow) { this.dedupWindow = dedupWindow; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }
}
