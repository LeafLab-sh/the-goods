package sh.leaflab.goods.economy;

import java.math.BigDecimal;

// Fixed-point currency representation: a long counting units of 10^-10 (10 decimal places), matching the
// balance's ~±922M headroom (Long.MAX_VALUE / 1e10 ~= 922,337,203.68). Never round-trips through double/float —
// full-precision parsing/formatting goes through BigDecimal and plain integer division so the 10th decimal digit
// is always exact.
public final class Currency {
    public static final long SCALE = 10_000_000_000L;

    private Currency() {
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
