package sh.leaflab.goods.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Currency has zero Minecraft/NeoForge dependencies, so it's covered by real unit tests rather than GameTest —
// see CLAUDE.md. Expected fixed-point values below were computed via jshell using the exact same
// StrictMath/BigDecimal calls Currency itself makes, not re-derived by hand, since the formulas involve
// StrictMath.log1p and floating-point rounding that isn't safe to hand-compute to 10 decimal places.
class CurrencyTest {
    @Test
    void sellValueOfFirstUnitEqualsOneCurrencyUnitExactly() {
        // log1p(1/1)/ln2 == log2(2) == 1.0 exactly — the one sellValue case with a clean closed-form answer.
        assertEquals(Currency.SCALE, Currency.sellValue(0, 1));
    }

    @Test
    void sellValueDiminishesAsStockGrows() {
        long first = Currency.sellValue(1, 1);
        long tenth = Currency.sellValue(9, 1);
        assertEquals(5_849_625_007L, first);
        assertEquals(1_375_035_237L, tenth);
    }

    @Test
    void sellingZeroQuantityPaysNothing() {
        assertEquals(0, Currency.sellValue(0, 0));
        assertEquals(0, Currency.sellValue(500, 0));
    }

    @Test
    void buyCostOfLastUnitInStockEqualsOneCurrencyUnitExactly() {
        // -log1p(-1/2)/ln2 == log2(2) == 1.0 exactly — buying the sole unit of a stock of 1.
        assertEquals(Currency.SCALE, Currency.buyCost(1, 1));
    }

    @Test
    void buyCostRisesWithQuantity() {
        assertEquals(1_520_030_935L, Currency.buyCost(9, 1));
        assertEquals(5_145_731_729L, Currency.buyCost(9, 3));
    }

    @Test
    void buyingZeroQuantityCostsNothing() {
        assertEquals(0, Currency.buyCost(9, 0));
    }

    @ParameterizedTest
    @CsvSource({
            "0,   5145731729",
            "5,   5403018315",
            "25,  6432164661",
            "50,  7718597593",
            "100, 10291463457",
    })
    void buyCostWithFeeAppliesFeeToRawValueBeforeRounding(int feePercent, long expectedUnits) {
        // Security-critical per Currency's own Javadoc: the fee must be applied before the single rounding step,
        // never after, or client preview and server charge can disagree.
        assertEquals(expectedUnits, Currency.buyCostWithFee(9, 3, feePercent));
    }

    @Test
    void zeroFeeMatchesPlainBuyCost() {
        assertEquals(Currency.buyCost(9, 3), Currency.buyCostWithFee(9, 3, 0));
    }

    @Test
    void hundredPercentFeeDoublesTheRawCostBeforeRounding() {
        double raw = Currency.buyRawCost(9, 3);
        assertEquals(Currency.ceilToFixedPoint(raw * 2.0), Currency.buyCostWithFee(9, 3, 100));
    }

    @Test
    void floorAndCeilRoundInOppositeDirectionsForPositiveValues() {
        assertEquals(1_234_567_891L, Currency.floorToFixedPoint(0.1234567891234));
        assertEquals(1_234_567_892L, Currency.ceilToFixedPoint(0.1234567891234));
    }

    @Test
    void floorAndCeilRoundInOppositeDirectionsForNegativeValues() {
        // Floor moves toward -infinity (more negative), ceil moves toward +infinity (less negative) — the
        // opposite of what "round down"/"round up" mean for positive values.
        assertEquals(-1_234_567_892L, Currency.floorToFixedPoint(-0.1234567891234));
        assertEquals(-1_234_567_891L, Currency.ceilToFixedPoint(-0.1234567891234));
    }

    @Test
    void floorAndCeilAgreeOnExactZero() {
        assertEquals(0, Currency.floorToFixedPoint(0.0));
        assertEquals(0, Currency.ceilToFixedPoint(0.0));
    }

    @Test
    void saturatingAddClampsOnPositiveOverflow() {
        assertEquals(Long.MAX_VALUE, Currency.saturatingAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, Currency.saturatingAdd(Long.MAX_VALUE - 5, 5));
    }

    @Test
    void saturatingAddClampsOnNegativeOverflow() {
        assertEquals(Long.MIN_VALUE, Currency.saturatingAdd(Long.MIN_VALUE, -1));
    }

    @Test
    void saturatingAddDoesNotFalsePositiveNearTheBoundary() {
        assertEquals(Long.MAX_VALUE - 1, Currency.saturatingAdd(Long.MAX_VALUE - 5, 4));
        assertEquals(300, Currency.saturatingAdd(100, 200));
    }

    @Test
    void parseExactAndFormatRoundTripThroughNegativeFractions() {
        long units = Currency.parseExact("-1.5");
        assertEquals(-15_000_000_000L, units);
        assertEquals("-1.5000000000", Currency.format(units));
        assertEquals(units, Currency.parseExact(Currency.format(units)));
    }

    @Test
    void parseExactAcceptsUpToTenFractionalDigits() {
        assertEquals(1_001_234_567_890L, Currency.parseExact("100.1234567890"));
    }

    @Test
    void parseExactRejectsMoreThanTenFractionalDigits() {
        assertThrows(NumberFormatException.class, () -> Currency.parseExact("100.12345678901"));
    }

    @ParameterizedTest
    @CsvSource({
            "0,        0",
            "999.99,   999.99",
            "999.999,  999.99",   // floors within a tier, doesn't round up to the next tier
            "1000,     1K",
            "999999.999, 999.99K",
            "1000000,  1M",
            "2000,     2K",       // trailing zeros stripped
            "2500,     2.5K",
            "-2500,    -2.5K",
            "1000000000000000000000000, 1Y",
            "1000000000000000000000000000, 1000Y", // capped at the top tier, doesn't invent a 9th suffix
    })
    void formatAbbreviatedMatchesInfiniteStorageCellTierSchemeButFloors(double value, String expected) {
        assertEquals(expected, Currency.formatAbbreviated(value));
    }

    @Test
    void buyThenSellRoundTripIsNeverProfitable() {
        // spec.md: "This makes a buy-then-sell round trip never profitable — break-even at best, at exact
        // integer log boundaries where there's nothing to round." Buy 5 into a stock of 100, then immediately
        // sell the same 5 back — floor(sell) must be strictly less than ceil(buy) for the same quantity/stock
        // window. The two raw values are algebraically identical (buying k at stock j and selling k back at
        // stock j-k measure the same window from opposite ends), so a strict comparison is what actually
        // distinguishes floor from ceil — a non-strict `<=` would pass even if the rounding policy were broken
        // entirely (e.g. buyCost floored instead of ceiled, or sellValue ceiled instead of floored).
        long stockBefore = 100;
        long quantity = 5;

        long buyCost = Currency.buyCost(stockBefore, quantity);
        long stockAfterBuy = stockBefore - quantity;
        long sellPayout = Currency.sellValue(stockAfterBuy, quantity);

        assertTrue(sellPayout < buyCost, "selling back what was just bought must never pay out as much as it cost");
    }

    @Test
    void sellThenBuyRoundTripIsNeverProfitable() {
        // The other direction: sell 5 into a stock of 100, then immediately buy the same 5 back. Strict
        // inequality matters here for the same reason as buyThenSellRoundTripIsNeverProfitable above.
        long stockBefore = 100;
        long quantity = 5;

        long sellPayout = Currency.sellValue(stockBefore, quantity);
        long stockAfterSell = stockBefore + quantity;
        long buyCost = Currency.buyCost(stockAfterSell, quantity);

        assertTrue(buyCost > sellPayout, "buying back what was just sold must never cost as little as it paid out");
    }
}
