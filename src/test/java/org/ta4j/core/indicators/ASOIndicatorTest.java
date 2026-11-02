package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ASOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public ASOIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void matchesPineFormula() {
        int length = 10;
        var indicator = new ASOIndicator(data, length);

        for (int i = data.getEndIndex() - 20; i <= data.getEndIndex(); i++) {
            assertNumEquals(referenceAso(i, length), indicator.getValue(i));
        }
    }

    @Test
    public void staysWithinPercentRange() {
        var indicator = new ASOIndicator(data, 10);

        for (int i = indicator.getCountOfUnstableBars(); i <= data.getEndIndex(); i++) {
            double value = indicator.getValue(i).doubleValue();
            assertTrue(value >= 0 && value <= 100, "ASO out of range at " + i + ": " + value);
        }
    }

    @Test
    public void lengthOneDoesNotThrow() {
        var indicator = new ASOIndicator(data, 1);

        assertNumEquals(referenceAso(data.getEndIndex(), 1), indicator.getValue(data.getEndIndex()));
    }

    /** Straight port of the TradingView script (mode 0), computed with doubles. */
    private double referenceAso(int index, int length) {
        double sum = 0;
        for (int j = index - length + 1; j <= index; j++) {
            Bar bar = data.getBar(j);
            double open = bar.getOpenPrice().doubleValue();
            double high = bar.getHighPrice().doubleValue();
            double low = bar.getLowPrice().doubleValue();
            double close = bar.getClosePrice().doubleValue();

            double groupLow = Double.MAX_VALUE;
            double groupHigh = -Double.MAX_VALUE;
            for (int k = j - length + 1; k <= j; k++) {
                groupLow = Math.min(groupLow, data.getBar(k).getLowPrice().doubleValue());
                groupHigh = Math.max(groupHigh, data.getBar(k).getHighPrice().doubleValue());
            }
            double groupOpen = data.getBar(j - length + 1).getOpenPrice().doubleValue();

            double k1 = high - low == 0 ? 1 : high - low;
            double k2 = groupHigh - groupLow == 0 ? 1 : groupHigh - groupLow;
            double intraBarBulls = (((close - low) + (high - open)) / 2) * 100 / k1;
            double groupBulls = (((close - groupLow) + (groupHigh - groupOpen)) / 2) * 100 / k2;
            sum += (intraBarBulls + groupBulls) / 2;
        }
        return sum / length;
    }
}
