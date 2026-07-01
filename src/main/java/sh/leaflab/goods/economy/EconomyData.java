package sh.leaflab.goods.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import sh.leaflab.goods.TheGoods;

public class EconomyData extends SavedData {
    public static final int DATA_VERSION = 1;

    // data_version is written on every save (always the current DATA_VERSION) but not otherwise used yet — it
    // exists so a future migration can tell an old save file apart from a current one before this mod has ever
    // needed to change its format.
    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("data_version").forGetter(data -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("balances").forGetter(EconomyData::balances)
    ).apply(instance, (loadedDataVersion, balances) -> new EconomyData(balances)));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TheGoods.MODID, "economy"),
            () -> new EconomyData(new HashMap<>()),
            CODEC
    );

    private final Map<UUID, Long> balances;

    private EconomyData(Map<UUID, Long> balances) {
        this.balances = new HashMap<>(balances);
    }

    private Map<UUID, Long> balances() {
        return balances;
    }

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    public void setBalance(UUID player, long amount) {
        balances.put(player, amount);
        setDirty();
    }
}
