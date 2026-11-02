package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;
    private ClosePriceIndicator close;

    public SMMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
        close = new ClosePriceIndicator(data);
    }

    @Test
    public void startsAfterLeadingNaN() {
        // NaN for the first 3 bars, then close[i - 3]
        var smma = new SMMAIndicator(new PreviousValueIndicator(close, 3), 5);

        for (int i = 0; i < 3; i++) {
            assertTrue(smma.getValue(i).isNaN());
        }
        double close0 = close.getValue(0).doubleValue();
        double close1 = close.getValue(1).doubleValue();
        assertNumEquals(close0, smma.getValue(3));
        assertNumEquals((close0 * 4 + close1) / 5, smma.getValue(4));
        assertEquals(3 + 5, smma.getCountOfUnstableBars());
    }

    @Test
    public void restartsAfterNaNInTheMiddle() {
        int nanIndex = 10;
        Indicator<Num> withGap = new AbstractIndicator<>(data) {
            @Override
            public Num getValue(int index) {
                return index == nanIndex ? NaN.NaN : close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        var smma = new SMMAIndicator(withGap, 5);

        assertTrue(Num.isFinite(smma.getValue(nanIndex - 1)));
        assertTrue(smma.getValue(nanIndex).isNaN());

        double next = close.getValue(nanIndex + 1).doubleValue();
        double afterNext = close.getValue(nanIndex + 2).doubleValue();
        assertNumEquals(next, smma.getValue(nanIndex + 1));
        assertNumEquals((next * 4 + afterNext) / 5, smma.getValue(nanIndex + 2));
    }

    @Test
    public void matchesWilderAverageWithoutNaN() {
        var smma = new SMMAIndicator(close, 14);

        double expected = close.getValue(0).doubleValue();
        for (int i = 1; i <= data.getEndIndex(); i++) {
            expected = (expected * 13 + close.getValue(i).doubleValue()) / 14;
        }
        assertNumEquals(expected, smma.getValue(data.getEndIndex()));
    }
}
