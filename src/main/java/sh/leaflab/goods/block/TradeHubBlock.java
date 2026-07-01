package sh.leaflab.goods.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import sh.leaflab.goods.menu.TradeHubMenu;

// Interacting from any side opens the trade UI (see docs/spec.md) — no more item-specific useItemOn override:
// selling now happens through the Sell Slot inside the menu, not by right-clicking with an item in hand.
public class TradeHubBlock extends Block {
    public TradeHubBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, p) -> new TradeHubMenu(windowId, inventory),
                    Component.translatable("block.thegoods.trade_hub")));
        }
        return InteractionResult.SUCCESS;
    }
}
