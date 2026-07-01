package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Client -> server. No price is sent — the server always re-derives cost from live stock; quoteHash is only used
// to tell a stale quote from a fresh one, never trusted for pricing.
public record BuyRequestPayload(Identifier item, long quantity, int quoteHash) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BuyRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "buy_request"));

    public static final StreamCodec<ByteBuf, BuyRequestPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, BuyRequestPayload::item,
            ByteBufCodecs.VAR_LONG, BuyRequestPayload::quantity,
            ByteBufCodecs.VAR_INT, BuyRequestPayload::quoteHash,
            BuyRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
