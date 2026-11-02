package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Smoothed moving average (SMMA) indicator, based on TradingView built-in indicator.
 */
public class SMMAIndicator extends RecursiveCachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final int barCount;

    public SMMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override protected Num calculate(int index) {
        Num current = indicator.getValue(index);

        if (index == 0 || !Num.isFinite(current)) {
            return current;
        }

        Num previous = getValue(index - 1);

        // Restart from the current value after a NaN, e.g. an EMA input still in its warm-up phase.
        // Otherwise a single NaN would poison every following value.
        if (!Num.isFinite(previous)) {
            return current;
        }

        return previous.multipliedBy(getBarSeries().numFactory().numOf(barCount - 1)).plus(current).dividedBy(
                getBarSeries().numFactory().numOf(barCount));
    }

    @Override public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount;
    }
}
