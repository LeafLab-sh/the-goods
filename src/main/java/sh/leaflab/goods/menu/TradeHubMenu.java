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

import sh.leaflab.goods.Config;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.Stock;
import sh.leaflab.goods.economy.TradeService;
import sh.leaflab.goods.network.BalanceSyncPayload;
import sh.leaflab.goods.network.BuyRequestPayload;
import sh.leaflab.goods.network.BuyResultPayload;
import sh.leaflab.goods.network.CatalogQueryPayload;
import sh.leaflab.goods.network.CatalogResultPayload;
import sh.leaflab.goods.registry.ModMenuTypes;

public class TradeHubMenu extends AbstractContainerMenu {
    private static final int SELL_SLOT_INDEX = 0;

    private final SimpleContainer sellContainer = new SimpleContainer(1);
    private final ServerPlayer serverPlayerOwner;
    // Long.MIN_VALUE so the very first broadcastChanges() always sends an initial sync, even if the balance is 0.
    private long lastSyncedBalance = Long.MIN_VALUE;
    private long clientBalance;

    // Server-side: whatever the client last asked the catalog to show, re-answered whenever stock changes so the
    // client doesn't have to re-ask (see broadcastChanges). Client-side: the latest page/result received.
    private CatalogQueryPayload lastCatalogQuery;
    private long lastSyncedStockEpoch = -1;
    private CatalogResultPayload clientCatalogResult;
    private BuyResultPayload clientLastBuyResult;

    // Layout constants shared with TradeHubScreen/CatalogWidget/BuyDialog: the catalog grid (9x4, 18px cells)
    // occupies (8,26) through (170,98) with a scrollbar at x=172-184 — the screen's full width is sized to just
    // fit that. Below the grid, the middle section (y=102-190) is split left/right by a vertical divider at
    // x=96: Sell heading + Sell Slot + balance on the left, the Buy interface on the right. Player inventory
    // follows after that.
    // Centered under the Sell heading within the left column (x=8 to the divider at x=96): center is (8+96)/2=52,
    // minus half the 18px slot width.
    public static final int SELL_SLOT_X = 43;
    public static final int SELL_SLOT_Y = 112;
    public static final int PLAYER_INVENTORY_Y = 192;

    public TradeHubMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.TRADE_HUB.get(), containerId);
        this.serverPlayerOwner = playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        this.addSlot(new SellSlot(sellContainer, 0, SELL_SLOT_X, SELL_SLOT_Y, playerInventory.player));
        this.addStandardInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
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

    // Pushes the player's balance to their client whenever it changes while this menu is open, and re-answers the
    // client's last catalog query whenever stock changes — both piggyback on broadcastChanges() already running
    // once per server tick for every open menu, so both are naturally coalesced across a tick's worth of trades.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverPlayerOwner == null) {
            return;
        }
        MinecraftServer server = serverPlayerOwner.level().getServer();

        long currentBalance = Economy.getBalance(server, serverPlayerOwner.getUUID());
        if (currentBalance != lastSyncedBalance) {
            lastSyncedBalance = currentBalance;
            PacketDistributor.sendToPlayer(serverPlayerOwner, new BalanceSyncPayload(currentBalance));
        }

        if (lastCatalogQuery != null) {
            long currentEpoch = Stock.getEpoch(server);
            if (currentEpoch != lastSyncedStockEpoch) {
                lastSyncedStockEpoch = currentEpoch;
                sendCatalogResult(lastCatalogQuery);
            }
        }
    }

    public void handleCatalogQuery(CatalogQueryPayload query) {
        if (serverPlayerOwner == null) {
            return;
        }
        this.lastCatalogQuery = query;
        this.lastSyncedStockEpoch = Stock.getEpoch(serverPlayerOwner.level().getServer());
        sendCatalogResult(query);
    }

    public void handleBuyRequest(BuyRequestPayload request) {
        if (serverPlayerOwner == null) {
            return;
        }
        TradeService.BuyOutcome outcome = TradeService.buy(serverPlayerOwner, request.item(), request.quantity(), request.quoteHash());
        PacketDistributor.sendToPlayer(serverPlayerOwner, new BuyResultPayload(outcome.success(), outcome.messageKey()));
    }

    private void sendCatalogResult(CatalogQueryPayload query) {
        MinecraftServer server = serverPlayerOwner.level().getServer();
        var entries = TradeService.queryCatalog(server, query.search(), query.sortKey(), query.ascending());
        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        PacketDistributor.sendToPlayer(serverPlayerOwner, new CatalogResultPayload(entries, feePercent));
    }

    public long getClientBalance() {
        return clientBalance;
    }

    public void setClientBalance(long balance) {
        this.clientBalance = balance;
    }

    public CatalogResultPayload getClientCatalogResult() {
        return clientCatalogResult;
    }

    public void setClientCatalogResult(CatalogResultPayload result) {
        this.clientCatalogResult = result;
    }

    public BuyResultPayload getClientLastBuyResult() {
        return clientLastBuyResult;
    }

    public void setClientLastBuyResult(BuyResultPayload result) {
        this.clientLastBuyResult = result;
    }
}
