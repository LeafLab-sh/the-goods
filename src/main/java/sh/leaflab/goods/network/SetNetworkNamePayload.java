package sh.leaflab.goods.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import sh.leaflab.goods.TheGoods;

// Client -> server: sets the network name on a Network Connector that the player is currently
// configuring. The server finds the connector via the player's open NetworkConnectorMenu.
public record SetNetworkNamePayload(String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetNetworkNamePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TheGoods.MODID, "set_network_name"));

    public static final StreamCodec<ByteBuf, SetNetworkNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetNetworkNamePayload::name,
            SetNetworkNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
