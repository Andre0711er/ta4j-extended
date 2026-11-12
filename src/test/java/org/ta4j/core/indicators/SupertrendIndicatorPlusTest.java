package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.indicators.ATRIndicatorPlus.Input;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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

    @Test
    public void flatBarsWithZeroAtrDoNotThrow() {
        // high == low == close gives an ATR of 0, so both bands equal the close and the previous value can equal the
        // previous close
        BarSeries flat = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = i < 25 ? 100 : 100 + (i - 25) * (i % 2 == 0 ? 1 : -1);
        }
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < closes.length; i++) {
            flat.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(start.plus(Duration.ofMinutes(i + 1)))
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .closePrice(closes[i])
                    .volume(1)
                    .add();
        }

        for (Input input : Input.values()) {
            var supertrend = new SupertrendIndicatorPlus(flat, 10, 3.0, input);

            assertDoesNotThrow(() -> {
                for (int i = 0; i <= flat.getEndIndex(); i++) {
                    supertrend.getValue(i);
                }
            }, input.name());
            for (int i = supertrend.getCountOfUnstableBars(); i <= flat.getEndIndex(); i++) {
                assertTrue(Num.isFinite(supertrend.getValue(i)), input.name() + " index " + i);
            }
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
