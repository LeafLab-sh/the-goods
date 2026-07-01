package sh.leaflab.goods.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// The single entry point for selling — reused by the Sell Slot GUI (Milestone 5) and, once buying exists, by the
// buy packet handler (Milestone 6), so the eligibility/value/persistence logic is written once, not per surface.
public final class TradeService {
    private TradeService() {
    }

    /**
     * Sells the whole of {@code stack} (does not mutate it — the caller is responsible for removing the item from
     * wherever it came from on success).
     *
     * @return true if the sale went through
     */
    public static boolean sell(ServerPlayer player, ItemStack stack) {
        if (!ItemEligibility.isEligible(stack)) {
            player.sendSystemMessage(Component.translatable("block.thegoods.trade_hub.not_eligible"));
            return false;
        }

        MinecraftServer server = player.level().getServer();
        Item item = stack.getItem();
        long quantity = stack.getCount();

        long stockBefore = Stock.getStock(server, item);
        long payout = Currency.sellValue(stockBefore, quantity);

        Economy.give(server, player.getUUID(), payout);
        Stock.credit(server, item, quantity);

        player.sendSystemMessage(Component.translatable(
                "block.thegoods.trade_hub.sold", quantity, stack.getHoverName(), Currency.format(payout)));
        return true;
    }
}
