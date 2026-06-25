package vdt.mini.management_service.dto.event;

import vdt.mini.management_service.util.enums.AnomalyDecision;

import java.util.List;

public record RuleEvaluationResult(AnomalyDecision decision, List<RuleMatch> matches) {
    public static RuleEvaluationResult normal() {
        return new RuleEvaluationResult(AnomalyDecision.NORMAL, List.of());
    }
}
