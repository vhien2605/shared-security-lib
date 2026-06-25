package vdt.mini.management_service.dto.event;

import java.time.Instant;
import java.util.List;

public record LogBaselineSnapshot(AnomalyGroupKey groupKey,
                                  long sampleCount,
                                  Double medianDurationMs,
                                  Double p95DurationMs,
                                  Double p99DurationMs,
                                  Double durationIqr,
                                  Double medianRequestSizeBytes,
                                  Double p95RequestSizeBytes,
                                  Double requestSizeIqr,
                                  Double medianResponseSizeBytes,
                                  Double p95ResponseSizeBytes,
                                  Double responseSizeIqr,
                                  Double medianMessageSizeBytes,
                                  Double p95MessageSizeBytes,
                                  Double messageSizeIqr,
                                  Double medianRetryAttempt,
                                  Double p95RetryAttempt,
                                  Double retryAttemptIqr,
                                  List<String> knownClients,
                                  List<String> knownSourceIps,
                                  List<String> knownErrorCodes,
                                  String baselineVersion,
                                  Instant calculatedAt,
                                  boolean active) {
}
