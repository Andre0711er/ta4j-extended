package org.ta4j.core.indicators.gchannel;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Internal combined indicator that computes both the upper and lower G-Channel
 * band values together, returning them as a {@code Num[2]} array where index 0
 * is the upper band and index 1 is the lower band.
 *
 * <p>Both values are computed in a single indicator so the bands do not
 * reference each other. It extends {@link RecursiveCachedIndicator}, which
 * prefills the history iteratively, so a first query at a late index does not
 * overflow the stack.
 */
class GChannelBaseIndicator extends RecursiveCachedIndicator<Num[]> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num numBarCount;

    GChannelBaseIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.numBarCount = indicator.getBarSeries().numFactory().numOf(barCount);
    }

    @Override
    protected Num[] calculate(int index) {
        if (index == 0) {
            Num zero = getBarSeries().numFactory().zero();
            return new Num[]{zero, zero};
        }

        Num[] prev = getValue(index - 1);
        Num prevUpper = prev[0];
        Num prevLower = prev[1];

        Num price = indicator.getValue(index);
        Num diff = prevUpper.minus(prevLower).dividedBy(numBarCount);

        Num upper = price.max(prevUpper).minus(diff);
        Num lower = price.min(prevLower).plus(diff);

        return new Num[]{upper, lower};
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}

