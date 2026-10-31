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

public class SmoothedHeikinAshiIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public SmoothedHeikinAshiIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void producesFiniteCandlesAfterWarmUp() {
        int length = 10;
        var indicator = new SmoothedHeikinAshiIndicator(data, length);

        for (int i = length; i <= data.getEndIndex(); i++) {
            Bar bar = indicator.getValue(i);
            assertTrue(Num.isFinite(bar.getOpenPrice()), "open is NaN at " + i);
            assertTrue(Num.isFinite(bar.getHighPrice()), "high is NaN at " + i);
            assertTrue(Num.isFinite(bar.getLowPrice()), "low is NaN at " + i);
            assertTrue(Num.isFinite(bar.getClosePrice()), "close is NaN at " + i);
        }

        double[][] reference = referenceSmoothedHeikinAshi(length);
        for (int i = data.getEndIndex() - 20; i <= data.getEndIndex(); i++) {
            Bar bar = indicator.getValue(i);
            assertNumEquals(reference[i][0], bar.getOpenPrice());
            assertNumEquals(reference[i][1], bar.getHighPrice());
            assertNumEquals(reference[i][2], bar.getLowPrice());
            assertNumEquals(reference[i][3], bar.getClosePrice());
        }
    }

    @Test
    public void heikinAshiSeedsFirstBarLikePine() {
        var indicator = new HeikinAshiIndicator(data);
        Bar source = data.getBar(0);
        Bar ha = indicator.getValue(0);

        double open = source.getOpenPrice().doubleValue();
        double high = source.getHighPrice().doubleValue();
        double low = source.getLowPrice().doubleValue();
        double close = source.getClosePrice().doubleValue();

        assertNumEquals((open + close) / 2, ha.getOpenPrice());
        assertNumEquals((open + high + low + close) / 4, ha.getClosePrice());
    }

    /** EMA(len) of OHLC -> Heikin Ashi -> EMA(len) of OHLC, with ta4j's EMA warm-up. Rows are {open, high, low, close}. */
    private double[][] referenceSmoothedHeikinAshi(int length) {
        int n = data.getBarCount();
        double[][] smoothed = new double[n][4];
        double[][] ha = new double[n][4];
        double[][] result = new double[n][4];

        for (int i = 0; i < n; i++) {
            Bar bar = data.getBar(i);
            double[] raw = { bar.getOpenPrice().doubleValue(), bar.getHighPrice().doubleValue(),
                    bar.getLowPrice().doubleValue(), bar.getClosePrice().doubleValue() };
            smoothed[i] = ema(smoothed, raw, i, length);

            double o = smoothed[i][0], h = smoothed[i][1], l = smoothed[i][2], c = smoothed[i][3];
            double haClose = (o + h + l + c) / 4;
            double haOpen = i == 0 || Double.isNaN(ha[i - 1][0]) ? (o + c) / 2 : (ha[i - 1][0] + ha[i - 1][3]) / 2;
            ha[i] = new double[] { haOpen, Math.max(h, Math.max(haOpen, haClose)),
                    Math.min(l, Math.min(haOpen, haClose)), haClose };

            result[i] = ema(result, ha[i], i, length);
        }
        return result;
    }

    private static double[] ema(double[][] previous, double[] source, int index, int length) {
        double alpha = 2.0 / (length + 1);
        double[] out = new double[4];
        for (int k = 0; k < 4; k++) {
            if (index < length || Double.isNaN(source[k])) {
                out[k] = Double.NaN;
            } else if (Double.isNaN(previous[index - 1][k])) {
                out[k] = source[k];
            } else {
                out[k] = previous[index - 1][k] + (source[k] - previous[index - 1][k]) * alpha;
            }
        }
        return out;
    }
}
