package sh.leaflab.goods.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Server -> client: one page of catalog results. Re-sent for whatever query the client last made whenever stock
// changes while the menu stays open (TradeHubMenu#broadcastChanges runs once per server tick per open menu, so
// this is naturally coalesced across however many trades happened that tick, not sent per-transaction).
public record CatalogResultPayload(List<CatalogEntry> entries, int page, int totalPages, int feePercent) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CatalogResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "catalog_result"));

    public static final StreamCodec<ByteBuf, CatalogResultPayload> STREAM_CODEC = StreamCodec.composite(
            CatalogEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), CatalogResultPayload::entries,
            ByteBufCodecs.VAR_INT, CatalogResultPayload::page,
            ByteBufCodecs.VAR_INT, CatalogResultPayload::totalPages,
            ByteBufCodecs.VAR_INT, CatalogResultPayload::feePercent,
            CatalogResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
