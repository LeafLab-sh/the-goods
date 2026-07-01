package sh.leaflab.goods.economy;

import java.math.BigDecimal;

// Fixed-point currency representation: a long counting units of 10^-10 (10 decimal places), matching the
// balance's ~±922M headroom (Long.MAX_VALUE / 1e10 ~= 922,337,203.68). Never round-trips through double/float —
// full-precision parsing/formatting goes through BigDecimal and plain integer division so the 10th decimal digit
// is always exact.
public final class Currency {
    public static final long SCALE = 10_000_000_000L;

    // StrictMath (not Math) is mandatory here: this value mints/destroys persisted currency, so it must be
    // bit-identical across JVMs/platforms. Math.log1p is permitted to use platform intrinsics; StrictMath isn't.
    private static final double LN2 = StrictMath.log(2.0);

    private Currency() {
    }

    /**
     * Value gained by selling {@code quantity} more into a stock of {@code stockBefore}, in real (unscaled)
     * currency units. Computed via a single log1p call to avoid catastrophic cancellation at large stock — see
     * {@link #buyRawCost}.
     */
    public static double sellRawValue(long stockBefore, long quantity) {
        return StrictMath.log1p(quantity / (stockBefore + 1.0)) / LN2;
    }

    /** Cost to buy {@code quantity} out of a stock of {@code stockBefore}, in real (unscaled) currency units. */
    public static double buyRawCost(long stockBefore, long quantity) {
        return -StrictMath.log1p(-quantity / (stockBefore + 1.0)) / LN2;
    }

    /** Sell payout as fixed-point units, floor-rounded (a sale can pay 0, never a fraction of a unit). */
    public static long sellValue(long stockBefore, long quantity) {
        return floorToFixedPoint(sellRawValue(stockBefore, quantity));
    }

    /** Buy cost as fixed-point units, ceil-rounded (a purchase can never cost 0 if it costs anything at all). */
    public static long buyCost(long stockBefore, long quantity) {
        return ceilToFixedPoint(buyRawCost(stockBefore, quantity));
    }

    /**
     * Buy cost with a percentage fee applied to the raw value before the single rounding step, so client preview
     * and server charge always agree exactly — the fee must never be added after rounding.
     */
    public static long buyCostWithFee(long stockBefore, long quantity, int feePercent) {
        double withFee = buyRawCost(stockBefore, quantity) * (1.0 + feePercent / 100.0);
        return ceilToFixedPoint(withFee);
    }

    /**
     * Converts a raw (unscaled) currency amount to fixed-point units, rounding down. Exposed so callers that need
     * to apply something (like a transaction fee) to a raw value before rounding — the value must be rounded once,
     * at the very end, not rounded and then adjusted, or the wash-trading exploit this rounding policy exists to
     * close reopens.
     */
    public static long floorToFixedPoint(double rawValue) {
        return (long) StrictMath.floor(rawValue * SCALE);
    }

    /** @see #floorToFixedPoint */
    public static long ceilToFixedPoint(double rawValue) {
        return (long) StrictMath.ceil(rawValue * SCALE);
    }

    /** Parses a decimal string into fixed-point units. Rejects more than 10 fractional digits. */
    public static long parseExact(String input) {
        BigDecimal value = new BigDecimal(input);
        if (value.scale() > 10) {
            throw new NumberFormatException("Too many decimal places (max 10): " + input);
        }
        BigDecimal scaled = value.movePointRight(10);
        if (scaled.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || scaled.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
            throw new NumberFormatException("Value out of range: " + input);
        }
        return scaled.longValueExact();
    }

    /** Formats fixed-point units at full 10-decimal precision, e.g. for /goods balance. */
    public static String format(long units) {
        long whole = units / SCALE;
        long frac = Math.abs(units % SCALE);
        return whole + "." + String.format("%010d", frac);
    }

    /** Adds two fixed-point values, saturating at Long.MAX_VALUE/MIN_VALUE on overflow instead of wrapping. */
    public static long saturatingAdd(long a, long b) {
        long sum = a + b;
        boolean overflowed = ((a ^ sum) & (b ^ sum)) < 0;
        if (!overflowed) {
            return sum;
        }
        return a >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
