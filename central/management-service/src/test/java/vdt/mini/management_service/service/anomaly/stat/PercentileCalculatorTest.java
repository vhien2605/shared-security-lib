package vdt.mini.management_service.service.anomaly.stat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PercentileCalculatorTest {
    private final PercentileCalculator calculator = new PercentileCalculator();

    @Test
    void percentile_shouldIgnoreNullAndInterpolateDeterministically() {
        assertEquals(25.0, calculator.percentile(List.of(10, 20, 30, 40), 50));
        assertEquals(37.0, calculator.percentile(List.of(10, 20, 30, 40), 90));
    }

    @Test
    void percentile_shouldReturnNullForEmptyInput() {
        assertNull(calculator.percentile(List.of(), 50));
        assertNull(calculator.percentile(null, 50));
    }

    @Test
    void percentile_shouldRejectInvalidPercentile() {
        assertThrows(IllegalArgumentException.class, () -> calculator.percentile(List.of(1), -1));
    }
}
