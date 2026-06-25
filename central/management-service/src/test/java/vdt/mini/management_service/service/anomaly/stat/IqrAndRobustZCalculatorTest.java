package vdt.mini.management_service.service.anomaly.stat;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IqrAndRobustZCalculatorTest {
    @Test
    void iqr_shouldUsePercentileDifferenceAndIgnoreNull() {
        IqrCalculator calculator = new IqrCalculator(new PercentileCalculator());

        assertEquals(15.0, calculator.iqr(List.of(10, 20, 30, 40)));
        assertNull(calculator.iqr(List.of()));
    }

    @Test
    void robustZ_shouldReturnNullForMissingValueOrMedianAndUseEpsilon() {
        var properties = AnomalyTestFixtures.properties();
        properties.getRobustZ().setEpsilon(1.0);
        RobustZCalculator calculator = new RobustZCalculator(properties);

        assertNull(calculator.robustZ(null, 10, 1));
        assertNull(calculator.robustZ(10, null, 1));
        assertEquals(10.0, calculator.robustZ(20, 10, 0));
        assertEquals(-5.0, calculator.robustZ(5, 10, 1));
    }
}
