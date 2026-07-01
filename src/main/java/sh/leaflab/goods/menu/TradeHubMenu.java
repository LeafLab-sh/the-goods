package sh.leaflab.goods.menu;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.network.BalanceSyncPayload;
import sh.leaflab.goods.registry.ModMenuTypes;

public class TradeHubMenu extends AbstractContainerMenu {
    private static final int SELL_SLOT_INDEX = 0;

    private final SimpleContainer sellContainer = new SimpleContainer(1);
    private final ServerPlayer serverPlayerOwner;
    // Long.MIN_VALUE so the very first broadcastChanges() always sends an initial sync, even if the balance is 0.
    private long lastSyncedBalance = Long.MIN_VALUE;
    private long clientBalance;

    public TradeHubMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.TRADE_HUB.get(), containerId);
        this.serverPlayerOwner = playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        this.addSlot(new SellSlot(sellContainer, 0, 80, 35, playerInventory.player));
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (index == SELL_SLOT_INDEX || slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (!this.moveItemStackTo(original, SELL_SLOT_INDEX, SELL_SLOT_INDEX + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // Pushes the player's balance to their client whenever it changes while this menu is open — vanilla's
    // ContainerData only carries shorts, not enough range for a fixed-point long balance, so this is a plain
    // custom packet instead. broadcastChanges() already runs once per server tick for every open menu.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverPlayerOwner != null) {
            MinecraftServer server = serverPlayerOwner.level().getServer();
            long currentBalance = Economy.getBalance(server, serverPlayerOwner.getUUID());
            if (currentBalance != lastSyncedBalance) {
                lastSyncedBalance = currentBalance;
                PacketDistributor.sendToPlayer(serverPlayerOwner, new BalanceSyncPayload(currentBalance));
            }
        }
    }

    public long getClientBalance() {
        return clientBalance;
    }

    public void setClientBalance(long balance) {
        this.clientBalance = balance;
    }
}
