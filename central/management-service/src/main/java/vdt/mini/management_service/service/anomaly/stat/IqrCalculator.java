package vdt.mini.management_service.service.anomaly.stat;

import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class IqrCalculator {
    private final PercentileCalculator percentileCalculator;

    public IqrCalculator(PercentileCalculator percentileCalculator) {
        this.percentileCalculator = percentileCalculator;
    }

    public Double iqr(Collection<? extends Number> values) {
        Double q1 = percentileCalculator.percentile(values, 25);
        Double q3 = percentileCalculator.percentile(values, 75);
        return q1 == null || q3 == null ? null : q3 - q1;
    }
}
