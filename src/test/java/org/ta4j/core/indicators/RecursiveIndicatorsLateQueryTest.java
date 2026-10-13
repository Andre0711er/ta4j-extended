package org.ta4j.core.indicators;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.TrailingIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterFallingIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterRisingIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Every indicator whose {@code calculate} reads its own previous value must survive a first query at a late index (a backtest starts evaluating thousands of
 * warm-up bars into the series) and give exactly the values of a sequential evaluation. The late query runs on a thread with a small stack, so a plain
 * {@link CachedIndicator} that walks back to bar 0 fails here.
 */
public class RecursiveIndicatorsLateQueryTest {

    private static final int BARS = 20_000;
    // the ta4j-extended range filter is quadratic (nested smooth-range reads); fewer bars still overflow a plain CachedIndicator on the small stack
    private static final int RANGE_FILTER_BARS = 5_000;
    private static final long SMALL_STACK = 256 * 1024;

    static Stream<Arguments> indicators() {
        return Stream.of(
                Arguments.of("CoralTrend", BARS, (Function<BarSeries, Indicator<?>>) s -> new CoralTrendIndicator(s, 21, 0.4)),
                Arguments.of("LaguerreRSI", BARS, (Function<BarSeries, Indicator<?>>) s -> new LaguerreRSIIndicator(new ClosePriceIndicator(s), 0.5)),
                Arguments.of("VIDYA", BARS, (Function<BarSeries, Indicator<?>>) s -> new VIDYAIndicator(new ClosePriceIndicator(s), 14)),
                Arguments.of("SMMA", BARS, (Function<BarSeries, Indicator<?>>) s -> new SMMAIndicator(new ClosePriceIndicator(s), 14)),
                Arguments.of("JMA", BARS, (Function<BarSeries, Indicator<?>>) s -> new JMAIndicator(s, 7, 50, 2)),
                Arguments.of("CMA", BARS, (Function<BarSeries, Indicator<?>>) s -> new CMAIndicator(s, 20)),
                Arguments.of("HACOLT", BARS, (Function<BarSeries, Indicator<?>>) s -> new HACOLTIndicator(s, 55, 60, 1.1)),
                Arguments.of("HEMA", BARS, (Function<BarSeries, Indicator<?>>) s -> new HEMAIndicator(s, 20, 20)),
                Arguments.of("McGinley", BARS, (Function<BarSeries, Indicator<?>>) s -> new McGinleyDynamicIndicator(new ClosePriceIndicator(s), 14)),
                Arguments.of("Trailing", BARS, (Function<BarSeries, Indicator<?>>) s -> TrailingIndicator.ema(s, 20, 5_000, TrailingIndicator.Direction.UP)),
                Arguments.of("RangeFilterRising", RANGE_FILTER_BARS,
                        (Function<BarSeries, Indicator<?>>) s -> new RangeFilterRisingIndicator(new RangeFilterIndicator(s, 100, 3.0))),
                Arguments.of("RangeFilterFalling", RANGE_FILTER_BARS,
                        (Function<BarSeries, Indicator<?>>) s -> new RangeFilterFallingIndicator(new RangeFilterIndicator(s, 100, 3.0))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("indicators")
    public void aLateFirstQueryNeitherOverflowsNorChangesTheValues(String name, int bars, Function<BarSeries, Indicator<?>> factory)
            throws InterruptedException {
        BarSeries series = randomWalk(bars);
        Indicator<?> sequential = factory.apply(series);
        Object[] expected = new Object[bars];
        for (int i = 0; i < bars; i++) {
            expected[i] = sequential.getValue(i);
        }

        Indicator<?> jumped = factory.apply(series);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                jumped.getValue(bars - 1);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "late-query", SMALL_STACK);
        thread.start();
        thread.join();
        Assertions.assertNull(failure.get(), name + ": late first query failed with " + failure.get());

        for (int i = 0; i < bars; i++) {
            Assertions.assertTrue(same(expected[i], jumped.getValue(i)), name + " differs at index " + i);
        }
    }

    private static boolean same(Object expected, Object actual) {
        if (expected instanceof Num e && actual instanceof Num a) {
            return Double.doubleToLongBits(e.doubleValue()) == Double.doubleToLongBits(a.doubleValue());
        }
        return expected.equals(actual);
    }

    private static BarSeries randomWalk(int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withName("walk").build();
        Random random = new Random(7);
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        double price = 1000;
        for (int i = 0; i < bars; i++) {
            double open = price;
            price *= 1 + random.nextGaussian() * 0.01;
            double close = price;
            series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(start.plus(Duration.ofHours(i + 1)))
                    .openPrice(open)
                    .highPrice(Math.max(open, close) * 1.003)
                    .lowPrice(Math.min(open, close) * 0.997)
                    .closePrice(close)
                    .volume(1_000 + random.nextInt(1_000))
                    .add();
        }
        return series;
    }
}
