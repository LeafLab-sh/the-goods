package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Server -> client: current stock of whatever is staged in the Sell Slot (Sell Dialog mode), re-sent whenever
// stock changes while something is staged (TradeHubMenu#broadcastChanges). The item and quantity are already
// known client-side from the slot's own vanilla-synced contents; only the stock figure needed for computing the
// live payout preview (Currency.sellValue) isn't otherwise available to the client.
public record SellPreviewPayload(long stockBeforeSale) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SellPreviewPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "sell_preview"));

    public static final StreamCodec<ByteBuf, SellPreviewPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(SellPreviewPayload::new, SellPreviewPayload::stockBeforeSale);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
