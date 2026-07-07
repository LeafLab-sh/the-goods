package sh.leaflab.goods.economy;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import sh.leaflab.goods.TheGoods;

// Stock is partitioned by scope strings (see docs/spec.md "Stock scoping"):
//   "local:<dim>:<x>:<y>:<z>"  — standalone hub, isolated stock
//   "network:<name>"           — connector-linked hub, shared across all hubs on the same network name
// Data version 1 (single global Map<Item, Long>) is migrated to the "network:global" scope automatically.
public class StockData extends SavedData {
    public static final int DATA_VERSION = 2;

    public static final Codec<StockData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG))
                    .optionalFieldOf("scoped_stock", Map.of()).forGetter(StockData::scopedStock),
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG)
                    .optionalFieldOf("stock", Map.of()).forGetter(d -> Map.of()) // v1 fallback, never written
    ).apply(instance, (scopedStock, legacyStock) -> {
        if (!scopedStock.isEmpty()) {
            return new StockData(new HashMap<>(scopedStock));
        }
        Map<String, Map<Item, Long>> migrated = new HashMap<>();
        if (!legacyStock.isEmpty()) {
            migrated.put(Stock.DEFAULT_SCOPE, new HashMap<>(legacyStock));
        }
        return new StockData(migrated);
    }));

    public static final SavedDataType<StockData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TheGoods.MODID, "stock"),
            () -> new StockData(new HashMap<>()),
            CODEC
    );

    private final Map<String, Map<Item, Long>> stock;
    private final Map<String, Long> epoch;

    private StockData(Map<String, Map<Item, Long>> stock) {
        this.stock = stock;
        this.epoch = new HashMap<>();
        stock.keySet().forEach(scope -> epoch.put(scope, 0L));
    }

    private Map<String, Map<Item, Long>> scopedStock() {
        return stock;
    }

    public long getStock(String scope, Item item) {
        Map<Item, Long> scopeStock = stock.get(scope);
        if (scopeStock == null) {
            return 0L;
        }
        return scopeStock.getOrDefault(item, 0L);
    }

    public void setStock(String scope, Item item, long quantity) {
        stock.computeIfAbsent(scope, s -> new HashMap<>()).put(item, quantity);
        epoch.merge(scope, 1L, Long::sum);
        setDirty();
    }

    public long getEpoch(String scope) {
        return epoch.getOrDefault(scope, 0L);
    }

    public Map<Item, Long> positiveStock(String scope) {
        Map<Item, Long> scopeStock = stock.get(scope);
        Map<Item, Long> result = new HashMap<>();
        if (scopeStock != null) {
            scopeStock.forEach((item, quantity) -> {
                if (quantity > 0) {
                    result.put(item, quantity);
                }
            });
        }
        return result;
    }

    public Map<Item, Long> positiveStockAll() {
        Map<Item, Long> result = new HashMap<>();
        for (Map.Entry<String, Map<Item, Long>> entry : stock.entrySet()) {
            for (Map.Entry<Item, Long> itemEntry : entry.getValue().entrySet()) {
                if (itemEntry.getValue() > 0) {
                    result.merge(itemEntry.getKey(), itemEntry.getValue(), Long::sum);
                }
            }
        }
        return result;
    }
}
