package sh.leaflab.goods.economy;

import net.minecraft.resources.Identifier;

// Distinguishes a fresh buy quote (matches current stock + stock epoch) from a stale or forged one. Not itself a
// security boundary — the server always re-derives price from live stock regardless of what the client claims —
// just a cheap way to tell "your view is out of date, here's a refresh" apart from "this doesn't match anything
// real, log it" without the server tracking full quote history.
public final class QuoteHash {
    private QuoteHash() {
    }

    public static int of(Identifier item, long stock, long epoch) {
        int result = item.hashCode();
        result = 31 * result + Long.hashCode(stock);
        result = 31 * result + Long.hashCode(epoch);
        return result;
    }
}
