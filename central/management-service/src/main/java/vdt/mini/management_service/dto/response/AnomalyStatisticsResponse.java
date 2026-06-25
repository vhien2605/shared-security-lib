package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnomalyStatisticsResponse {
    private long totalAnomalies;
    private long criticalAnomalies;
    private long totalIncidents;
    private long affectedServices;
    private double averageRiskScore;
    private List<Bucket> byLevel;
    private List<Bucket> byType;
    private List<Bucket> byDecision;
    private List<TimelineBucket> timeline;
    private List<Bucket> topServices;
    private List<Bucket> topEndpoints;
    private List<Bucket> topMatchedRules;

    @Getter
    @Builder
    public static class Bucket {
        private String key;
        private long count;
    }

    @Getter
    @Builder
    public static class TimelineBucket {
        private String bucket;
        private long critical;
        private long high;
        private long total;
    }
}
