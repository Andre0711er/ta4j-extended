package org.ta4j.core.indicators.wae;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicatorPlus;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * Waddah Attar Explosion Dead Zone indicator by ShayanKM.
 * <a href="https://www.tradingview.com/script/d9IjcYyS-Waddah-Attar-Explosion-V2-SHK/">TradingView</a>
 */
public class WAEDeadZoneIndicator extends AbstractIndicator<Num> {
    private final Indicator<Num> deadZone;
    private final int barCount;

    public WAEDeadZoneIndicator(BarSeries series) {
        this(series, 100, 3.7);
    }

    public WAEDeadZoneIndicator(BarSeries series, int barCount, double multiplier) {
        this(series, new ATRIndicatorPlus(series, barCount), barCount, multiplier);
    }

    public WAEDeadZoneIndicator(BarSeries series, AbstractIndicator abstractIndicator) {
        this(series, abstractIndicator, 100, 3.7);
    }

    public WAEDeadZoneIndicator(BarSeries series, AbstractIndicator abstractIndicator, int barCount,
            double multiplier) {
        super(series);

        this.deadZone = TransformIndicator.multiply(abstractIndicator, multiplier);
        this.barCount = barCount;
    }

    @Override
    public Num getValue(int index) {
        return deadZone.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}
