package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestdataReader;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VortexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public VortexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() throws IOException {
        data = new TestdataReader(numFactory).readCsv("btcusdt-1d.csv");
    }

    @Test
    public void test() {
        var indicator = new VortexIndicator(data, 14);

        assertNumEquals(1.0838, indicator.getViPlus(data.getEndIndex() - 2));
        assertNumEquals(0.8870, indicator.getViMinus(data.getEndIndex() - 2));
        assertNumEquals(1.1334, indicator.getViPlus(data.getEndIndex() - 1));
        assertNumEquals(0.8556, indicator.getViMinus(data.getEndIndex() - 1));
        assertNumEquals(1.1754, indicator.getViPlus(data.getEndIndex()));
        assertNumEquals(0.8195, indicator.getViMinus(data.getEndIndex()));
    }

    @Test
    public void positiveAndNegativeValueShouldDelegateToViPlusAndViMinus() {
        var indicator = new VortexIndicator(data, 14);

        int[] indexes = { data.getEndIndex() - 2, data.getEndIndex() - 1, data.getEndIndex(),
                data.getBeginIndex() + 14, (data.getBeginIndex() + data.getEndIndex()) / 2 };
        for (int index : indexes) {
            assertNumEquals(indicator.getViPlus(index), indicator.getPositiveValue(index));
            assertNumEquals(indicator.getViMinus(index), indicator.getNegativeValue(index));
        }
    }

    @Test
    public void positiveAndNegativeValueShouldMatchKnownValues() {
        var indicator = new VortexIndicator(data, 14);

        assertNumEquals(1.0838, indicator.getPositiveValue(data.getEndIndex() - 2));
        assertNumEquals(0.8870, indicator.getNegativeValue(data.getEndIndex() - 2));
        assertNumEquals(1.1334, indicator.getPositiveValue(data.getEndIndex() - 1));
        assertNumEquals(0.8556, indicator.getNegativeValue(data.getEndIndex() - 1));
        assertNumEquals(1.1754, indicator.getPositiveValue(data.getEndIndex()));
        assertNumEquals(0.8195, indicator.getNegativeValue(data.getEndIndex()));
    }

    @Test
    public void positiveAndNegativeValueShouldBehaveLikeViPlusAndViMinusOnEarlyBars() {
        var indicator = new VortexIndicator(data, 14);

        int endEarlyIndex = Math.min(data.getBeginIndex() + 14, data.getEndIndex());
        for (int index = data.getBeginIndex(); index <= endEarlyIndex; index++) {
            Num viPlus = indicator.getViPlus(index);
            Num positive = indicator.getPositiveValue(index);
            Num viMinus = indicator.getViMinus(index);
            Num negative = indicator.getNegativeValue(index);

            assertFalse("VI+ must not be null at index " + index, viPlus == null || positive == null);
            assertFalse("VI- must not be null at index " + index, viMinus == null || negative == null);
            if (viPlus.isNaN() || positive.isNaN()) {
                assertTrue("VI+ and positive value must both be NaN at index " + index,
                        viPlus.isNaN() && positive.isNaN());
            } else {
                assertNumEquals(viPlus, positive);
            }
            if (viMinus.isNaN() || negative.isNaN()) {
                assertTrue("VI- and negative value must both be NaN at index " + index,
                        viMinus.isNaN() && negative.isNaN());
            } else {
                assertNumEquals(viMinus, negative);
            }
        }
    }

    @Test
    public void unstableBarsAndDefaultValueShouldRemainUnchanged() {
        var indicator = new VortexIndicator(data, 14);

        assertEquals(14, indicator.getCountOfUnstableBars());
        assertNumEquals(0, indicator.getValue(data.getEndIndex()));
    }
}
