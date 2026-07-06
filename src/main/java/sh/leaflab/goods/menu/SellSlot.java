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

public class SellSlot extends Slot {
    private final Player owner;
    private final BooleanSupplier sellDialogEnabled;
    private final String scope;

    public SellSlot(Container container, int index, int x, int y, Player owner, BooleanSupplier sellDialogEnabled, String scope) {
        super(container, index, x, y);
        this.owner = owner;
        this.sellDialogEnabled = sellDialogEnabled;
        this.scope = scope;
    }

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

    @Override
    public void setByPlayer(ItemStack itemStack, ItemStack previous) {
        if (itemStack.isEmpty() || !(owner instanceof ServerPlayer serverPlayer) || sellDialogEnabled.getAsBoolean()) {
            super.setByPlayer(itemStack, previous);
            return;
        }
        TradeService.sell(serverPlayer, itemStack, scope);
    }
}
