package sh.leaflab.goods.economy;

import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

public final class Stock {
    private Stock() {
    }

    public static long getStock(MinecraftServer server, Item item) {
        return data(server).getStock(item);
    }

    public static long getEpoch(MinecraftServer server) {
        return data(server).getEpoch();
    }

    public static Map<Item, Long> positiveStock(MinecraftServer server) {
        return data(server).positiveStock();
    }

    /** Increases stock by {@code quantity} (e.g. after a sell), saturating rather than overflowing. */
    public static void credit(MinecraftServer server, Item item, long quantity) {
        StockData data = data(server);
        data.setStock(item, Currency.saturatingAdd(data.getStock(item), quantity));
        flush(server);
    }

    /** Decreases stock by {@code quantity} (e.g. after a buy). Caller must have already checked stock is sufficient. */
    public static void debit(MinecraftServer server, Item item, long quantity) {
        StockData data = data(server);
        data.setStock(item, Math.max(0L, data.getStock(item) - quantity));
        flush(server);
    }

    // Always fetched from the overworld, matching Economy — one stock ledger for the whole server, not per dimension.
    private static StockData data(MinecraftServer server) {
        return SavedDataAccess.get(server, StockData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
}
