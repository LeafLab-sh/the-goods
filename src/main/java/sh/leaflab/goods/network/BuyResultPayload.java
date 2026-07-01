package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Server -> client: outcome of a BuyRequestPayload, so the Buy Dialog can show a clean result without parsing chat.
public record BuyResultPayload(boolean success, String messageKey) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BuyResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "buy_result"));

    public static final StreamCodec<ByteBuf, BuyResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BuyResultPayload::success,
            ByteBufCodecs.STRING_UTF8, BuyResultPayload::messageKey,
            BuyResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
