package sh.leaflab.goods.economy;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import sh.leaflab.goods.Config;
import sh.leaflab.goods.TheGoods;

public class AuditData extends SavedData {
    public static final int DATA_VERSION = 1;

    public static final Codec<AuditData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("data_version").forGetter(data -> DATA_VERSION),
            AuditEntry.CODEC.listOf().fieldOf("entries").forGetter(AuditData::entries)
    ).apply(instance, (dataVersion, entries) -> new AuditData(entries)));

    public static final SavedDataType<AuditData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TheGoods.MODID, "audit"),
            () -> new AuditData(new ArrayList<>()),
            CODEC
    );

    private final List<AuditEntry> entries;

    private AuditData(List<AuditEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    private List<AuditEntry> entries() {
        return entries;
    }

    public void addEntry(AuditEntry entry) {
        entries.add(entry);
        int maxEntries = Config.AUDIT_LOG_MAX_ENTRIES.get();
        if (maxEntries > 0) {
            while (entries.size() > maxEntries) {
                entries.removeFirst();
            }
        }
        setDirty();
    }

    public List<AuditEntry> latestEntries(int count) {
        int size = entries.size();
        int from = Math.max(0, size - count);
        List<AuditEntry> result = new ArrayList<>(Math.min(count, size));
        for (int i = size - 1; i >= from; i--) {
            result.add(entries.get(i));
        }
        return result;
    }

    public int size() {
        return entries.size();
    }
}
