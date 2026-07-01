package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Server -> client: the receiving player's current balance (fixed-point units, see Currency), sent whenever it
// changes while a TradeHubMenu is open.
public record BalanceSyncPayload(long balance) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BalanceSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "balance_sync"));

    public static final StreamCodec<ByteBuf, BalanceSyncPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(BalanceSyncPayload::new, BalanceSyncPayload::balance);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
