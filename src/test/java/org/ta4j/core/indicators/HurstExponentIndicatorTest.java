package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HurstExponentIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private static final int BAR_COUNT = 100;

    private BarSeries data;

    public HurstExponentIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void isNaNDuringWarmUpAndFiniteAfterwards() {
        // DecimalNum used to fail at index 0 converting the NaN warm-up mean
        var hurst = new HurstExponentIndicator(data, BAR_COUNT);

        for (int i = 0; i < BAR_COUNT; i++) {
            assertTrue(hurst.getValue(i).isNaN(), "index " + i);
        }
        for (int i = BAR_COUNT; i <= data.getEndIndex(); i++) {
            assertTrue(Num.isFinite(hurst.getValue(i)), "index " + i);
        }
    }

    @Test
    public void matchesReferenceCalculation() {
        var hurst = new HurstExponentIndicator(data, BAR_COUNT);

        for (int index : new int[] { BAR_COUNT, 200, data.getEndIndex() }) {
            assertEquals(reference(index), hurst.getValue(index).doubleValue(), 1e-9, "index " + index);
        }
    }

    @Test
    public void rejectsBarCountBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> new HurstExponentIndicator(data, 1));
        assertThrows(IllegalArgumentException.class, () -> new HurstExponentIndicator(data, 0));
    }

    /**
     * Plain double implementation of the QuantNomad script for the window ending at
     * {@code index}.
     */
    private double reference(int index) {
        int from = index - BAR_COUNT + 1;
        double[] pnl = new double[BAR_COUNT];
        double mean = 0;
        for (int i = from; i <= index; i++) {
            pnl[i - from] = data.getBar(i).getClosePrice().doubleValue()
                    / data.getBar(i - 1).getClosePrice().doubleValue() - 1;
            mean += pnl[i - from];
        }
        mean /= BAR_COUNT;

        double cum = 0;
        double cumMin = Double.MAX_VALUE;
        double cumMax = Double.MIN_VALUE;
        double devSum = 0;
        for (double p : pnl) {
            cum += p - mean;
            cumMin = Math.min(cumMin, cum);
            cumMax = Math.max(cumMax, cum);
            devSum += (p - mean) * (p - mean);
        }
        double sd = Math.sqrt(devSum / (BAR_COUNT - 1));
        return Math.log((cumMax - cumMin) / sd) / Math.log(BAR_COUNT);
    }
}
