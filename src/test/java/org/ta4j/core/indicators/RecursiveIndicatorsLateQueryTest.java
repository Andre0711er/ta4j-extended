package org.ta4j.core.indicators;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.RandomWalkSeries;
import org.ta4j.core.indicators.gchannel.GChannelFacade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.TrailingIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterFallingIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterIndicator;
import org.ta4j.core.indicators.rangefilter.RangeFilterRisingIndicator;
import org.ta4j.core.indicators.volume.VolumeFlowIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Every indicator whose {@code calculate} reads its own previous value must survive a first query at a late index (a
 * backtest starts evaluating thousands of warm-up bars into the series) and give exactly the values of a sequential
 * evaluation. Late queries run on a thread with a small stack, so a plain {@link CachedIndicator} that walks back to
 * bar 0 fails here.
 */
public class RecursiveIndicatorsLateQueryTest {

    private static final int BARS = 10_000;
    // the ta4j-extended range filter is quadratic (nested smooth-range reads); fewer bars still overflow a plain
    // CachedIndicator on the small stack
    private static final int RANGE_FILTER_BARS = 5_000;
    private static final int GROWTH_BARS = 200;
    private static final long SMALL_STACK = 256 * 1024;

    private record Case(String name, int bars, Function<BarSeries, Indicator<?>> factory) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Case of(String name, int bars, Function<BarSeries, Indicator<?>> factory) {
        return new Case(name, bars, factory);
    }

    private static List<Case> cases() {
        return List.of(
                of("CoralTrend", BARS, s -> new CoralTrendIndicator(s, 21, 0.4)),
                of("LaguerreRSI", BARS, s -> new LaguerreRSIIndicator(new ClosePriceIndicator(s), 0.5)),
                of("VIDYA", BARS, s -> new VIDYAIndicator(new ClosePriceIndicator(s), 14)),
                of("SMMA", BARS, s -> new SMMAIndicator(new ClosePriceIndicator(s), 14)),
                of("JMA", BARS, s -> new JMAIndicator(s, 7, 50, 2)),
                of("CMA", BARS, s -> new CMAIndicator(s, 20)),
                of("HACOLT", BARS, s -> new HACOLTIndicator(s, 55, 60, 1.1)),
                of("HEMA", BARS, s -> new HEMAIndicator(s, 20, 20)),
                of("McGinley", BARS, s -> new McGinleyDynamicIndicator(new ClosePriceIndicator(s), 14)),
                of("SupertrendPlus", BARS, s -> new SupertrendIndicatorPlus(s, 10, 3.0, ATRIndicatorPlus.Input.SMA)),
                of("FantailVMA", BARS, s -> new FantailVMAIndicator(s, 2, 10, 6)),
                // not self-recursive and very slow with DecimalNum, so fewer bars
                of("VolumeFlow", 2_000, s -> new VolumeFlowIndicator(s, 130, 0.2, 2.5)),
                of("Trailing", BARS, s -> TrailingIndicator.ema(s, 20, 5_000, TrailingIndicator.Direction.UP)),
                of("GChannelUpper", BARS, s -> new GChannelFacade(new ClosePriceIndicator(s), 100).upper()),
                of("GChannelLower", BARS, s -> new GChannelFacade(new ClosePriceIndicator(s), 100).lower()),
                of("RangeFilterRising", RANGE_FILTER_BARS,
                        s -> new RangeFilterRisingIndicator(new RangeFilterIndicator(s, 100, 3.0))),
                of("RangeFilterFalling", RANGE_FILTER_BARS,
                        s -> new RangeFilterFallingIndicator(new RangeFilterIndicator(s, 100, 3.0))));
    }

    static Stream<Arguments> doubleAndDecimalCases() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())
                .flatMap(factory -> cases().stream().map(c -> Arguments.of(factory.getClass().getSimpleName(), factory, c)));
    }

    static Stream<Arguments> doubleCases() {
        return cases().stream().map(c -> Arguments.of(c));
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("doubleAndDecimalCases")
    public void lateFirstQueriesNeitherOverflowNorChangeTheValues(String factoryName, NumFactory numFactory, Case c)
            throws InterruptedException {
        BarSeries series = RandomWalkSeries.build(numFactory, c.bars());
        Indicator<?> jumped = c.factory().apply(series);

        // first query in the middle, then the end, then backwards below the first cached index
        queryOnSmallStack(c.name(), jumped, c.bars() / 2);
        queryOnSmallStack(c.name(), jumped, c.bars() - 1);
        queryOnSmallStack(c.name(), jumped, 10);

        // the series keeps growing while the indicator is alive (live trading)
        RandomWalkSeries.append(series, GROWTH_BARS);
        queryOnSmallStack(c.name(), jumped, series.getEndIndex());

        Indicator<?> sequential = c.factory().apply(series);
        for (int i = 0; i <= series.getEndIndex(); i++) {
            sequential.getValue(i);
        }
        for (int i = 0; i <= series.getEndIndex(); i++) {
            assertSameValue(c.name(), i, sequential.getValue(i), jumped.getValue(i));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("doubleCases")
    public void concurrentLateQueriesOnOneInstanceDoNotDeadlock(Case c) throws Exception {
        BarSeries series = RandomWalkSeries.build(DoubleNumFactory.getInstance(), c.bars());
        Indicator<?> sequential = c.factory().apply(series);
        for (int i = 0; i < c.bars(); i++) {
            sequential.getValue(i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 5; round++) {
                Indicator<?> shared = c.factory().apply(series);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    int index = c.bars() - 1 - t * (c.bars() / 10);
                    futures.add(executor.submit(() -> shared.getValue(index)));
                }
                for (Future<?> future : futures) {
                    future.get(60, TimeUnit.SECONDS);
                }
                for (int i = 0; i < c.bars(); i += 97) {
                    assertSameValue(c.name(), i, sequential.getValue(i), shared.getValue(i));
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Values must agree up to rounding noise. They are not always bit-identical: ta4j's SMA (used inside CMA,
     * FantailVMA, ...) sums incrementally or directly depending on what is already cached, which differs in the last
     * digits at the last bar or after the series grew. A wrong recursion base would be off by far more than this.
     */
    private static void assertSameValue(String name, int index, Object expected, Object actual) {
        if (expected instanceof Num e && actual instanceof Num a) {
            double x = e.doubleValue();
            double y = a.doubleValue();
            boolean bothNaN = Double.isNaN(x) && Double.isNaN(y);
            Assertions.assertTrue(bothNaN || Math.abs(x - y) <= 1e-9 * Math.max(1, Math.abs(x)),
                    name + " differs at index " + index + ": expected " + x + " but was " + y);
        } else {
            Assertions.assertEquals(expected, actual, name + " differs at index " + index);
        }
    }

    private static void queryOnSmallStack(String name, Indicator<?> indicator, int index) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                indicator.getValue(index);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "late-query", SMALL_STACK);
        thread.start();
        thread.join();
        Assertions.assertNull(failure.get(), name + ": query at index " + index + " failed with " + failure.get());
    }
}
