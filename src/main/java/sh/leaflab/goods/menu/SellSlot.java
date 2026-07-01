package sh.leaflab.goods.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.economy.ItemEligibility;
import sh.leaflab.goods.economy.TradeService;

// Processes on insert and never actually stores anything — the backing container slot stays permanently empty, so
// every insert (drag-drop or shift-click) lands on an "empty" slot again, letting repeated shift-clicks keep working.
public class SellSlot extends Slot {
    private final Player owner;

    public SellSlot(Container container, int index, int x, int y, Player owner) {
        super(container, index, x, y);
        this.owner = owner;
    }

    // mayPlace isn't only called for real placement attempts — Slot#allowModification calls
    // mayPlace(this.getItem()) too, and this slot's backing item is always empty, so the empty-stack case must be
    // excluded here or every unrelated allowModification check would spuriously report "can't trade" on nothing.
    @Override
    public boolean mayPlace(ItemStack stack) {
        boolean eligible = ItemEligibility.isEligible(stack);
        if (!eligible && !stack.isEmpty() && owner instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("block.thegoods.trade_hub.not_eligible"));
        }
        return eligible;
    }

    // Fires on both sides (menu logic is mirrored client/server); only the server actually sells anything —
    // the client-side call here is just its own (identical, since we never store) prediction of the same no-op.
    @Override
    public void setByPlayer(ItemStack itemStack, ItemStack previous) {
        if (!itemStack.isEmpty() && owner instanceof ServerPlayer serverPlayer) {
            TradeService.sell(serverPlayer, itemStack);
        }
    }
}
