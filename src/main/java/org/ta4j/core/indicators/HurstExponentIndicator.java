package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Simple Hurst Exponent indicator by QuantNomad.
 * <a href="https://www.tradingview.com/script/v267CGzx-Simple-Hurst-Exponent-QuantNomad/">TradingView</a>
 */
public class HurstExponentIndicator extends CachedIndicator<Num> {
    private final BinaryOperationIndicator pnl;
    private final SMAIndicator meanPnl;
    private final int barCount;

    public HurstExponentIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    public HurstExponentIndicator(ClosePriceIndicator indicator, int barCount) {
        super(indicator.getBarSeries());

        this.pnl = BinaryOperationIndicator.difference(BinaryOperationIndicator.quotient(indicator, new PreviousValueIndicator(indicator)),
                1);
        this.meanPnl = new SMAIndicator(pnl, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        NumFactory numFactory = getBarSeries().numFactory();
        Num mean = meanPnl.getValue(index);
        int from = index - barCount + 1;

        // pnl needs a previous close, so the first full window starts at bar 1
        if (from < 1 || mean.isNaN()) {
            return NaN.NaN;
        }

        Num cum = numFactory.zero();
        Num cumMin = numFactory.numOf(Double.MAX_VALUE);
        Num cumMax = numFactory.numOf(Double.MIN_VALUE);
        Num devSum = numFactory.zero();

        for (int i = from; i <= index; i++) {
            Num distanceFromMean = pnl.getValue(i).minus(mean);
            cum = cum.plus(distanceFromMean);
            cumMin = cumMin.min(cum);
            cumMax = cumMax.max(cum);
            devSum = devSum.plus(distanceFromMean.multipliedBy(distanceFromMean));
        }

        Num sd = devSum.dividedBy(numFactory.numOf(barCount - 1)).sqrt();
        Num rs = cumMax.minus(cumMin).dividedBy(sd);

        return rs.log().dividedBy(numFactory.numOf(barCount).log());
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}
