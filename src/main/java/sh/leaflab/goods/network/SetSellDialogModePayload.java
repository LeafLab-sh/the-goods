package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Client -> server: the player's Sell Dialog toggle (Quick Sell vs. stage-then-confirm). Purely a UI preference,
// not a security boundary, so the server just trusts it — a malicious client claiming "Quick Sell" when the real
// toggle says otherwise gains nothing, since Quick Sell is already the more permissive/default behavior.
public record SetSellDialogModePayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetSellDialogModePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "set_sell_dialog_mode"));

    public static final StreamCodec<ByteBuf, SetSellDialogModePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(SetSellDialogModePayload::new, SetSellDialogModePayload::enabled);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
