package sh.leaflab.goods.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.SavedDataStorage;

public final class Stock {
    private Stock() {
    }

    public static long getStock(MinecraftServer server, Item item) {
        return data(server).getStock(item);
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
        return dataStorage(server).computeIfAbsent(StockData.TYPE);
    }

    private static SavedDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }

    private static void flush(MinecraftServer server) {
        dataStorage(server).saveAndJoin();
    }
}
