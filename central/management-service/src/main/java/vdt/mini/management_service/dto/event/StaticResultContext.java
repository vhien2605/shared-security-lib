package vdt.mini.management_service.dto.event;

public record StaticResultContext(String status,
                                  String resultCode,
                                  String errorCode,
                                  String denyReason,
                                  Long remainingQuota,
                                  Integer retryAttempt,
                                  String rollbackStrategy,
                                  boolean failed,
                                  boolean denied,
                                  boolean retried) {
}
