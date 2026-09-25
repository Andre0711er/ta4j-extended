package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class FantailVMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public FantailVMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void test() {
        var indicator = new FantailVMAIndicator(data, 2, 10, 6);

        assertNumEquals(100575.13812438415, indicator.getValue(data.getBarCount() - 3));
        assertNumEquals(101244.0238346954, indicator.getValue(data.getBarCount() - 2));
        assertNumEquals(102067.44049672519, indicator.getValue(data.getEndIndex()));
    }

    @Test
    public void directJumpToLateIndexDoesNotOverflow() {
        BarSeries series = largeSeries(5_000);
        var indicator = new FantailVMAIndicator(series, 2, 10, 6);

        Assert.assertNotNull(indicator.getValue(series.getEndIndex()));
    }

    @Test
    public void jumpedValueEqualsSequentialValue() {
        BarSeries series = largeSeries(5_000);

        var sequential = new FantailVMAIndicator(series, 2, 10, 6);
        for (int i = 0; i <= series.getEndIndex(); i++) {
            sequential.getValue(i);
        }

        var jumped = new FantailVMAIndicator(series, 2, 10, 6);
        assertNumEquals(sequential.getValue(series.getEndIndex()).doubleValue(),
                jumped.getValue(series.getEndIndex()));
    }

    private BarSeries largeSeries(int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("fantail-soe").build();
        Instant start = Instant.parse("2022-01-01T00:00:00Z");
        for (int i = 0; i < bars; i++) {
            double price = 1000 + i * 0.5 + 20 * Math.sin(i * 0.3);
            series.barBuilder()
                    .timePeriod(Duration.ofHours(4))
                    .endTime(start.plus(Duration.ofHours(4L * (i + 1))))
                    .openPrice(price)
                    .highPrice(price + 5)
                    .lowPrice(price - 5)
                    .closePrice(price + 1)
                    .volume(1_000)
                    .add();
        }
        return series;
    }
}
