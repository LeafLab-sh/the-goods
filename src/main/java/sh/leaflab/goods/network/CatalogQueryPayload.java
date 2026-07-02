package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Client -> server: what the catalog widget currently wants to see. Re-sent on every search/sort change, and the
// server remembers the last one per menu so it can re-answer it (see TradeHubMenu#broadcastChanges) whenever
// stock changes, without the client having to re-ask. No page/scroll position here — the client receives the full
// filtered+sorted result set and scrolls through it locally (see CatalogWidget).
public record CatalogQueryPayload(String search, String sortKey, boolean ascending) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CatalogQueryPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "catalog_query"));

    public static final StreamCodec<ByteBuf, CatalogQueryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CatalogQueryPayload::search,
            ByteBufCodecs.STRING_UTF8, CatalogQueryPayload::sortKey,
            ByteBufCodecs.BOOL, CatalogQueryPayload::ascending,
            CatalogQueryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
