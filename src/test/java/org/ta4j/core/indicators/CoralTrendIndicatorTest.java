package org.ta4j.core.indicators;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CoralTrendIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    public CoralTrendIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void firstQueryAtHighIndexDoesNotOverflowStack() {
        BarSeries series = largeSeries(30_000);
        var jumped = new CoralTrendIndicator(series, 21, 0.4);

        Assertions.assertDoesNotThrow(() -> jumped.getValue(series.getEndIndex()));
    }

    @Test
    public void jumpedQueryMatchesSequentialBitExact() {
        BarSeries series = largeSeries(30_000);
        var sequential = new CoralTrendIndicator(series, 21, 0.4);
        for (int i = 0; i <= series.getEndIndex(); i++) {
            sequential.getValue(i);
        }

        var jumped = new CoralTrendIndicator(series, 21, 0.4);
        for (int index : new int[] { series.getEndIndex(), 15_000, 250, 0 }) {
            Assertions.assertEquals(sequential.getValue(index).doubleValue(), jumped.getValue(index).doubleValue(),
                    "index " + index);
        }
    }

    private BarSeries largeSeries(int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("coral").build();
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        for (int i = 0; i < bars; i++) {
            double price = 1000 + i * 0.01 + 20 * Math.sin(i * 0.3);
            series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(start.plus(Duration.ofHours(i + 1)))
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
