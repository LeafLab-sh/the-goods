package sh.leaflab.goods.economy;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

// requester is asking to receive `amount`; payer is the one who must accept before it's actually sent.
public record TradeRequest(UUID requester, UUID payer, long amount) {
    public static final Codec<TradeRequest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("requester").forGetter(TradeRequest::requester),
            UUIDUtil.STRING_CODEC.fieldOf("payer").forGetter(TradeRequest::payer),
            Codec.LONG.fieldOf("amount").forGetter(TradeRequest::amount)
    ).apply(instance, TradeRequest::new));
}
