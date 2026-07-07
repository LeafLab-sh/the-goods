package sh.leaflab.goods.economy;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

public record AuditEntry(
        long gameTime,
        String action,
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetName,
        long amount,
        long targetBalance,
        String itemId,
        long itemQuantity
) {
    public AuditEntry {
        if (itemId == null) itemId = "";
    }

    public static final Codec<AuditEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("game_time").forGetter(AuditEntry::gameTime),
            Codec.STRING.fieldOf("action").forGetter(AuditEntry::action),
            UUIDUtil.STRING_CODEC.fieldOf("actor_id").forGetter(AuditEntry::actorId),
            Codec.STRING.fieldOf("actor_name").forGetter(AuditEntry::actorName),
            UUIDUtil.STRING_CODEC.fieldOf("target_id").forGetter(AuditEntry::targetId),
            Codec.STRING.fieldOf("target_name").forGetter(AuditEntry::targetName),
            Codec.LONG.fieldOf("amount").forGetter(AuditEntry::amount),
            Codec.LONG.fieldOf("target_balance").forGetter(AuditEntry::targetBalance),
            Codec.STRING.optionalFieldOf("item_id", "").forGetter(AuditEntry::itemId),
            Codec.LONG.optionalFieldOf("item_quantity", 0L).forGetter(AuditEntry::itemQuantity)
    ).apply(instance, AuditEntry::new));
}
