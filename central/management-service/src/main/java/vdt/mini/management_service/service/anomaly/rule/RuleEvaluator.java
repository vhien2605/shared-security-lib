package vdt.mini.management_service.service.anomaly.rule;

import vdt.mini.management_service.dto.event.AnomalyContext;
import vdt.mini.management_service.dto.event.RuleEvaluationResult;

public interface RuleEvaluator {
    RuleEvaluationResult evaluate(AnomalyContext context);
}
