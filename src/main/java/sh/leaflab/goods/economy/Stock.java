package sh.leaflab.goods.economy;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class Stock {
    public static final String DEFAULT_SCOPE = "network:global";

    private Stock() {
    }

    public static long getStock(MinecraftServer server, String scope, Item item) {
        return data(server).getStock(scope, item);
    }

    public static long getEpoch(MinecraftServer server, String scope) {
        return data(server).getEpoch(scope);
    }

    public static Map<Item, Long> positiveStock(MinecraftServer server, String scope) {
        return data(server).positiveStock(scope);
    }

    public static Map<Item, Long> positiveStockAll(MinecraftServer server) {
        return data(server).positiveStockAll();
    }

    public static void credit(MinecraftServer server, String scope, Item item, long quantity) {
        StockData data = data(server);
        data.setStock(scope, item, Currency.saturatingAdd(data.getStock(scope, item), quantity));
        flush(server);
    }

    public static void debit(MinecraftServer server, String scope, Item item, long quantity) {
        StockData data = data(server);
        data.setStock(scope, item, Math.max(0L, data.getStock(scope, item) - quantity));
        flush(server);
    }

    public static String localScope(Level level, BlockPos pos) {
        Identifier dimId = level.dimension().identifier();
        return "local:" + dimId + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static StockData data(MinecraftServer server) {
        return SavedDataAccess.get(server, StockData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
}
