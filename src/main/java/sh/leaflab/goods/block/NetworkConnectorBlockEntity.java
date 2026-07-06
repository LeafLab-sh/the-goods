package sh.leaflab.goods.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import sh.leaflab.goods.registry.ModBlockEntities;

public class NetworkConnectorBlockEntity extends BlockEntity {
    private static final String TAG_NETWORK_NAME = "network_name";

    private String networkName = "";

    public NetworkConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_CONNECTOR.get(), pos, state);
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String name) {
        this.networkName = name != null ? name : "";
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(TAG_NETWORK_NAME, networkName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.networkName = input.getStringOr(TAG_NETWORK_NAME, "");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(TAG_NETWORK_NAME, networkName);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
