package sh.leaflab.goods.menu;

import java.util.function.BooleanSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.economy.ItemEligibility;
import sh.leaflab.goods.economy.TradeService;

// Two modes, switched per-player by the Sell Dialog toggle (sellDialogEnabled):
// - Quick Sell (default): processes on insert and never actually stores anything — the backing container slot
//   stays permanently empty, so every insert (drag-drop or shift-click) lands on an "empty" slot again, letting
//   repeated shift-clicks keep working.
// - Sell Dialog: insert actually stages the item in the container (via the real Slot#setByPlayer/set path,
//   skipped entirely in Quick Sell mode) instead of processing it, so the player can preview the payout and
//   Confirm/Cancel — see TradeHubMenu#handleSellDecision and #removed for how a staged item leaves the slot.
public class SellSlot extends Slot {
    private final Player owner;
    private final BooleanSupplier sellDialogEnabled;

    public SellSlot(Container container, int index, int x, int y, Player owner, BooleanSupplier sellDialogEnabled) {
        super(container, index, x, y);
        this.owner = owner;
        this.sellDialogEnabled = sellDialogEnabled;
    }

    // mayPlace isn't only called for real placement attempts — Slot#allowModification calls
    // mayPlace(this.getItem()) too. Under Quick Sell this slot's backing item is always empty, so the
    // empty-stack case must be excluded from messaging or every unrelated allowModification check would
    // spuriously report "can't trade" on nothing. Under Sell Dialog, the slot can genuinely hold one staged
    // item — right-clicking with a compatible held stack should top it up (vanilla's normal same-item merge
    // behavior), so only a *different* item is blocked while something's already staged, not same-item topping-up.
    @Override
    public boolean mayPlace(ItemStack stack) {
        ItemStack current = this.getItem();
        if (!current.isEmpty() && !current.is(stack.getItem())) {
            return false;
        }
        boolean eligible = ItemEligibility.isEligible(stack);
        if (!eligible && !stack.isEmpty() && owner instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("block.thegoods.trade_hub.not_eligible"));
        }
        return eligible;
    }

    // Fires on both sides (menu logic is mirrored client/server); only the server actually sells/stages
    // anything — the client-side call here is its own prediction of the same outcome.
    @Override
    public void setByPlayer(ItemStack itemStack, ItemStack previous) {
        if (itemStack.isEmpty() || !(owner instanceof ServerPlayer serverPlayer) || sellDialogEnabled.getAsBoolean()) {
            super.setByPlayer(itemStack, previous);
            return;
        }
        TradeService.sell(serverPlayer, itemStack);
    }
}
