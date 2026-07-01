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

// Stock is keyed by Item alone, not by full ItemStack + components: only items in default component state are
// ever tradeable (see ItemEligibility), and there's exactly one default-component variant per Item, so a
// composite ItemStack-shaped key would carry a permutation this mod can never actually reach.
public class StockData extends SavedData {
    public static final int DATA_VERSION = 1;

    public static final Codec<StockData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("data_version").forGetter(data -> DATA_VERSION),
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG).fieldOf("stock").forGetter(StockData::stock)
    ).apply(instance, (loadedDataVersion, stock) -> new StockData(stock)));

    public static final SavedDataType<StockData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TheGoods.MODID, "stock"),
            () -> new StockData(new HashMap<>()),
            CODEC
    );

    private final Map<Item, Long> stock;
    // In-memory only (not persisted): increments on every stock change, used to tell a buy quote "still fresh" from
    // "stale" (see QuoteHash) and to know when a menu's live catalog view needs refreshing. Resetting to 0 across a
    // restart is fine — no quote survives a restart anyway.
    private long epoch;

    private StockData(Map<Item, Long> stock) {
        this.stock = new HashMap<>(stock);
    }

    private Map<Item, Long> stock() {
        return stock;
    }

    public long getStock(Item item) {
        return stock.getOrDefault(item, 0L);
    }

    public void setStock(Item item, long quantity) {
        stock.put(item, quantity);
        epoch++;
        setDirty();
    }

    public long getEpoch() {
        return epoch;
    }

    /** All items with positive stock. Returns a defensive copy — safe to iterate while trades continue elsewhere. */
    public Map<Item, Long> positiveStock() {
        Map<Item, Long> result = new HashMap<>();
        stock.forEach((item, quantity) -> {
            if (quantity > 0) {
                result.put(item, quantity);
            }
        });
        return result;
    }
}
