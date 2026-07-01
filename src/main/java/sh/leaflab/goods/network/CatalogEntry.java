package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record CatalogEntry(Identifier item, long stock, int quoteHash) {
    public static final StreamCodec<ByteBuf, CatalogEntry> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CatalogEntry::item,
            ByteBufCodecs.VAR_LONG, CatalogEntry::stock,
            ByteBufCodecs.VAR_INT, CatalogEntry::quoteHash,
            CatalogEntry::new
    );
}
