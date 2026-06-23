package vdt.mini.management_service.dto.event;

public record HistoricalDeviation(Double durationRobustZ,
                                  Double requestSizeRobustZ,
                                  Double responseSizeRobustZ,
                                  Double messageSizeRobustZ,
                                  Double retryAttemptRobustZ,
                                  boolean rareClient,
                                  boolean rareSourceIp,
                                  boolean rareErrorCode) {
}
