package vdt.mini.management_service.service.anomaly.stat;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PercentileCalculator {
    public Double percentile(Collection<? extends Number> values, double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be between 0 and 100");
        }
        List<Double> sorted = values == null ? List.of() : values.stream()
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        double rank = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = rank - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * weight;
    }
}
