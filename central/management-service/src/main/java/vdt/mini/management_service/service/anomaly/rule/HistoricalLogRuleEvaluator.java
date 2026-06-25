package vdt.mini.management_service.service.anomaly.rule;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.ArrayList;
import java.util.List;

@Service
public class HistoricalLogRuleEvaluator implements RuleEvaluator {
    @Override
    public RuleEvaluationResult evaluate(AnomalyContext context) {
        if (context == null || context.historicalDeviation() == null || context.confidence() == null || !context.confidence().hasHistoricalLogBaseline()) {
            return RuleEvaluationResult.normal();
        }
        HistoricalDeviation deviation = context.historicalDeviation();
        List<RuleMatch> matches = new ArrayList<>();
        addZ(matches, deviation.durationRobustZ(), 6.0, "HIST_LATENCY_001", AnomalyType.LATENCY_OUTLIER, "durationMs", "Duration is above historical robust-z threshold");
        addZ(matches, deviation.requestSizeRobustZ(), 6.0, "HIST_REQ_SIZE_001", AnomalyType.REQUEST_SIZE_OUTLIER, "requestSizeBytes", "Request size is above historical robust-z threshold");
        addZ(matches, deviation.responseSizeRobustZ(), 6.0, "HIST_RES_SIZE_001", AnomalyType.RESPONSE_SIZE_OUTLIER, "responseSizeBytes", "Response size is above historical robust-z threshold");
        addZ(matches, deviation.messageSizeRobustZ(), 6.0, "HIST_MSG_SIZE_001", AnomalyType.MESSAGE_SIZE_OUTLIER, "messageSizeBytes", "Message size is above historical robust-z threshold");
        addZ(matches, deviation.retryAttemptRobustZ(), 5.0, "HIST_RETRY_001", AnomalyType.RETRY_OUTLIER, "retryAttempt", "Retry attempt is above historical robust-z threshold");
        addRare(matches, deviation.rareClient(), "HIST_RARE_CLIENT_001", AnomalyType.NEW_OR_RARE_CLIENT, "clientId");
        addRare(matches, deviation.rareSourceIp(), "HIST_RARE_SOURCE_IP_001", AnomalyType.NEW_OR_RARE_SOURCE_IP, "sourceIp");
        addRare(matches, deviation.rareErrorCode(), "HIST_RARE_ERROR_001", AnomalyType.RARE_ERROR_CODE, "errorCode");
        if (matches.isEmpty()) return RuleEvaluationResult.normal();
        boolean onlyRare = matches.stream().allMatch(match -> match.confidence() == RuleConfidence.LOW);
        return new RuleEvaluationResult(onlyRare ? AnomalyDecision.OBSERVE : AnomalyDecision.ANOMALY, matches);
    }

    private void addZ(List<RuleMatch> matches, Double z, double threshold, String ruleId, AnomalyType type, String feature, String description) {
        if (z != null && z >= threshold) {
            matches.add(new RuleMatch(ruleId, type, 5, RuleConfidence.HIGH, List.of(feature, feature + "RobustZ"), description));
        }
    }

    private void addRare(List<RuleMatch> matches, boolean matched, String ruleId, AnomalyType type, String feature) {
        if (matched) {
            matches.add(new RuleMatch(ruleId, type, 1, RuleConfidence.LOW, List.of(feature), feature + " is not present in active baseline"));
        }
    }
}
