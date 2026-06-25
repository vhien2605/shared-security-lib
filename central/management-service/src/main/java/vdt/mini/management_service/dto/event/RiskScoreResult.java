package vdt.mini.management_service.dto.event;

import java.util.Map;

public record RiskScoreResult(int totalScore,
                              int sourceSeverityPoints,
                              Map<String, Integer> contributions) {
}
