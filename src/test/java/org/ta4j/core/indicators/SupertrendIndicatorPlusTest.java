package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.indicators.ATRIndicatorPlus.Input;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SupertrendIndicatorPlusTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public SupertrendIndicatorPlusTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void defaultAtrInputSurvivesItsWarmUp() {
        // the default ATR (MMA) is NaN during its unstable bars, which used to throw at
        // index 1
        var supertrend = new SupertrendIndicatorPlus(data, 10, 3.0);

        assertDoesNotThrow(() -> {
            for (int i = 0; i <= data.getEndIndex(); i++) {
                supertrend.getValue(i);
            }
        });
        assertFiniteAfterUnstableBars(supertrend);
    }

    @Test
    public void everyAtrInputCanBeEvaluated() {
        for (Input input : Input.values()) {
            var supertrend = new SupertrendIndicatorPlus(data, 10, 3.0, input);

            assertDoesNotThrow(() -> supertrend.getValue(data.getEndIndex()), input.name());
            assertFiniteAfterUnstableBars(supertrend);
        }
    }

    private void assertFiniteAfterUnstableBars(SupertrendIndicatorPlus supertrend) {
        for (int i = supertrend.getCountOfUnstableBars(); i <= data.getEndIndex(); i++) {
            Num value = supertrend.getValue(i);
            assertTrue(Num.isFinite(value), "index " + i);
            assertFalse(value.isEqual(data.getBar(i).getClosePrice()), "index " + i);
        }
    }
}
