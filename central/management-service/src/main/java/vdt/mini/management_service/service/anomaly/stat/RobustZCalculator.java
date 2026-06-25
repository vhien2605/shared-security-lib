package vdt.mini.management_service.service.anomaly.stat;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;

@Service
public class RobustZCalculator {
    private final AnomalyDetectionProperties properties;

    public RobustZCalculator(AnomalyDetectionProperties properties) {
        this.properties = properties;
    }

    public Double robustZ(Number value, Number median, Number iqr) {
        if (value == null || median == null) {
            return null;
        }
        double denominator = Math.max(iqr == null ? 0.0 : Math.abs(iqr.doubleValue()), properties.getRobustZ().getEpsilon());
        return (value.doubleValue() - median.doubleValue()) / denominator;
    }
}
