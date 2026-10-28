package sh.leaflab.goods.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after a successful item purchase.
 * The stock, balance, and fee have already been updated by the time this event fires.
 */
public class ItemBoughtEvent extends Event {
    private final ServerPlayer player;
    private final Item item;
    private final long quantity;
    private final long cost;
    private final int feePercent;
    private final long feeAmount;
    private final long stockAfter;

    public ItemBoughtEvent(ServerPlayer player, Item item, long quantity, long cost, int feePercent, long feeAmount, long stockAfter) {
        this.player = player;
        this.item = item;
        this.quantity = quantity;
        this.cost = cost;
        this.feePercent = feePercent;
        this.feeAmount = feeAmount;
        this.stockAfter = stockAfter;
    }

    public ServerPlayer player() {
        return player;
    }

    public Item item() {
        return item;
    }

    public long quantity() {
        return quantity;
    }

    public long cost() {
        return cost;
    }

    public int feePercent() {
        return feePercent;
    }

    public long feeAmount() {
        return feeAmount;
    }

    public long stockAfter() {
        return stockAfter;
    }
}
