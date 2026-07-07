package sh.leaflab.goods.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import sh.leaflab.goods.block.NetworkConnectorBlockEntity;
import sh.leaflab.goods.registry.ModMenuTypes;

public class NetworkConnectorMenu extends AbstractContainerMenu {
    private final BlockPos connectorPos;

    public NetworkConnectorMenu(int containerId, Inventory playerInventory, BlockPos connectorPos) {
        super(ModMenuTypes.NETWORK_CONNECTOR.get(), containerId);
        this.connectorPos = connectorPos;
    }

    public NetworkConnectorMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.NETWORK_CONNECTOR.get(), containerId);
        this.connectorPos = null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (connectorPos == null) {
            return true;
        }
        return player.distanceToSqr(connectorPos.getX() + 0.5, connectorPos.getY() + 0.5, connectorPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Nullable
    public String getNetworkName(ServerPlayer player) {
        BlockEntity be = findConnector(player);
        return be instanceof NetworkConnectorBlockEntity connector ? connector.getNetworkName() : null;
    }

    public void setNetworkName(ServerPlayer player, String name) {
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        name = name.trim();
        if (name.isEmpty()) {
            name = "";
        }
        if (player.level().getBlockEntity(connectorPos) instanceof NetworkConnectorBlockEntity connector) {
            connector.setNetworkName(name);
        }
    }

    @Nullable
    private BlockEntity findConnector(ServerPlayer player) {
        if (connectorPos == null) return null;
        return player.level().getBlockEntity(connectorPos);
    }
}
