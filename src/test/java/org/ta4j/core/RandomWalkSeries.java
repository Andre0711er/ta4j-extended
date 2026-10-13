package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.ta4j.core.num.NumFactory;

/**
 * Deterministic random-walk hourly bar series for tests that need many bars.
 */
public final class RandomWalkSeries {
    private static final Duration PERIOD = Duration.ofHours(1);
    private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

    private RandomWalkSeries() {
    }

    public static BarSeries build(NumFactory numFactory, int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("random-walk").build();
        append(series, bars);
        return series;
    }

    /**
     * Appends {@code count} bars after the last bar of the series. The sequence of generated bars depends only on the
     * total bar count, so growing a series gives the same bars as building it in one go.
     */
    public static void append(BarSeries series, int count) {
        int first = series.getBarCount();
        Random random = new Random(7);
        double price = 1000;
        for (int i = 0; i < first + count; i++) {
            double open = price;
            price *= 1 + random.nextGaussian() * 0.01;
            double close = price;
            double high = Math.max(open, close) * 1.003;
            double low = Math.min(open, close) * 0.997;
            double volume = 1_000 + random.nextInt(1_000);
            if (i >= first) {
                series.barBuilder()
                        .timePeriod(PERIOD)
                        .endTime(START.plus(PERIOD.multipliedBy(i + 1)))
                        .openPrice(open)
                        .highPrice(high)
                        .lowPrice(low)
                        .closePrice(close)
                        .volume(volume)
                        .add();
            }
        }
    }
}
