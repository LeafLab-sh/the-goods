package sh.leaflab.goods.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import sh.leaflab.goods.menu.NetworkConnectorMenu;

// A block placed adjacent to a Trade Hub (touching any face) that connects the hub to a named network,
// sharing stock with every other hub on the same network name. Right-click to open a configuration
// screen where the player sets the network name.
public class NetworkConnectorBlock extends Block implements EntityBlock {
    public NetworkConnectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkConnectorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, p) -> new NetworkConnectorMenu(windowId, inventory, pos),
                    Component.translatable("block.thegoods.network_connector")));
        }
        return InteractionResult.SUCCESS;
    }
}
