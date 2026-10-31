package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SRSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public SRSIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void producesValuesAfterEmaWarmUp() {
        int length = 6;
        int smoothing = 14;
        var indicator = new SRSIIndicator(new ClosePriceIndicator(data), length, smoothing);

        // ta4j 0.26 EMA is NaN during its warm-up; SRSI must recover afterwards instead of staying NaN
        assertTrue(indicator.getValue(length - 1).isNaN());

        for (int i = indicator.getCountOfUnstableBars(); i <= data.getEndIndex(); i++) {
            double value = indicator.getValue(i).doubleValue();
            assertTrue(value >= 0 && value <= 100, "SRSI out of range at " + i + ": " + value);
        }

        double[] reference = referenceSrsi(length, smoothing);
        for (int i = data.getEndIndex() - 20; i <= data.getEndIndex(); i++) {
            assertNumEquals(reference[i], indicator.getValue(i));
        }
    }

    /** LazyBear's formula with ta4j's EMA warm-up (NaN for the first barCount bars, then seeded with the source). */
    private double[] referenceSrsi(int length, int smoothing) {
        int n = data.getBarCount();
        double alpha = 2.0 / (length + 1);
        double[] result = new double[n];
        double ema = Double.NaN;
        double r4 = Double.NaN;
        double r5 = Double.NaN;

        for (int i = 0; i < n; i++) {
            double src = data.getBar(i).getClosePrice().doubleValue();
            if (i < length) {
                result[i] = Double.NaN;
                continue;
            }
            ema = Double.isNaN(ema) ? src : ema + (src - ema) * alpha;
            double r2 = Math.max(src - ema, 0);
            double r3 = Math.max(ema - src, 0);
            r4 = Double.isNaN(r4) ? r2 : (r4 * (smoothing - 1) + r2) / smoothing;
            r5 = Double.isNaN(r5) ? r3 : (r5 * (smoothing - 1) + r3) / smoothing;
            result[i] = r5 == 0 ? 100 : 100 - 100 / (1 + r4 / r5);
        }
        return result;
    }
}
