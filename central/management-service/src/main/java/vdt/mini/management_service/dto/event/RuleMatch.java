package vdt.mini.management_service.dto.event;

import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.List;

public record RuleMatch(String ruleId,
                        AnomalyType anomalyType,
                        int riskPoints,
                        RuleConfidence confidence,
                        List<String> detectedFeatures,
                        String description) {
}
