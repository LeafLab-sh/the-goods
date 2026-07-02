package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Client -> server: Confirm or Cancel on whatever is currently staged in the Sell Slot (Sell Dialog mode only).
// No item/quantity fields — the server already knows exactly what's staged from the slot's own real contents,
// same source of truth as the client's icon/count display, so there's nothing here to echo back or trust.
public record SellDecisionPayload(boolean confirm) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SellDecisionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "sell_decision"));

    public static final StreamCodec<ByteBuf, SellDecisionPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(SellDecisionPayload::new, SellDecisionPayload::confirm);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
