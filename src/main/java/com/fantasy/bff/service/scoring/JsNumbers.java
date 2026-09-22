package com.fantasy.bff.service.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Number handling that has to match the browser's exactly, because the same board is scored on
 * both sides and a board is scored on the numbers as they are written.
 */
public final class JsNumbers {

    private JsNumbers() {
    }

    /**
     * What {@code parseFloat(value.toFixed(decimals))} gives in JavaScript.
     *
     * <p>Two things separate this from a plain {@code setScale(decimals, HALF_UP)}. It rounds the
     * double's <b>exact</b> binary value, not the short decimal string Java prints for it — so
     * 1.005, which is really 1.00499…, rounds down as it does in the browser. And on an exact
     * half it picks the larger number rather than the one further from zero, so -1.5 at no
     * decimals is -1: ECMA-262 says to take the larger candidate, which is where Java's HALF_UP
     * (away from zero) parts company.
     *
     * @param value the number as computed
     * @param decimals places to keep; negative is treated as none, as {@code toFixed(0)} is
     * @return the rounded value
     */
    public static double toFixed(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return value;
        }
        int scale = Math.max(0, decimals);
        RoundingMode mode = value < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP;
        return new BigDecimal(value).setScale(scale, mode).doubleValue();
    }
}
