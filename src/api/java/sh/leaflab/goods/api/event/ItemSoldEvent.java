package sh.leaflab.goods.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after a successful item sale.
 * The stock and balance have already been updated by the time this event fires.
 */
public class ItemSoldEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack stack;
    private final long quantity;
    private final long payout;
    private final long stockAfter;

    public ItemSoldEvent(ServerPlayer player, ItemStack stack, long quantity, long payout, long stockAfter) {
        this.player = player;
        this.stack = stack.copy();
        this.quantity = quantity;
        this.payout = payout;
        this.stockAfter = stockAfter;
    }

    public ServerPlayer player() {
        return player;
    }

    public ItemStack stack() {
        return stack.copy();
    }

    public long quantity() {
        return quantity;
    }

    public long payout() {
        return payout;
    }

    public long stockAfter() {
        return stockAfter;
    }
}
