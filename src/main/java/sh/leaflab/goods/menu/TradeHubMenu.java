package sh.leaflab.goods.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import sh.leaflab.goods.Config;
import sh.leaflab.goods.block.NetworkConnectorBlock;
import sh.leaflab.goods.block.NetworkConnectorBlockEntity;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.Stock;
import sh.leaflab.goods.economy.TradeService;
import sh.leaflab.goods.network.BalanceSyncPayload;
import sh.leaflab.goods.network.BuyRequestPayload;
import sh.leaflab.goods.network.BuyResultPayload;
import sh.leaflab.goods.network.CatalogQueryPayload;
import sh.leaflab.goods.network.CatalogResultPayload;
import sh.leaflab.goods.network.SellDecisionPayload;
import sh.leaflab.goods.network.SellPreviewPayload;
import sh.leaflab.goods.registry.ModMenuTypes;

public class TradeHubMenu extends AbstractContainerMenu {
    private static final int SELL_SLOT_INDEX = 0;

    private final SimpleContainer sellContainer = new SimpleContainer(1);
    private final ServerPlayer serverPlayerOwner;
    private final String scope;

    private long lastSyncedBalance = Long.MIN_VALUE;
    private long clientBalance;

    private CatalogQueryPayload lastCatalogQuery;
    private long lastSyncedStockEpoch = -1;
    private CatalogResultPayload clientCatalogResult;
    private BuyResultPayload clientLastBuyResult;

    private boolean sellDialogEnabled;
    private long lastSyncedSellStock = Long.MIN_VALUE;
    private long clientSellPreviewStock;

    public static final int SELL_SLOT_X = 43;
    public static final int SELL_SLOT_Y = 112;
    public static final int PLAYER_INVENTORY_Y = 192;

    // Server-side constructor: determines stock scope from adjacent Network Connector.
    public TradeHubMenu(int containerId, Inventory playerInventory, BlockPos hubPos) {
        super(ModMenuTypes.TRADE_HUB.get(), containerId);
        this.serverPlayerOwner = playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.scope = hubPos != null && serverPlayerOwner != null
                ? resolveScope(serverPlayerOwner.level(), hubPos)
                : Stock.DEFAULT_SCOPE;

        this.addSlot(new SellSlot(sellContainer, 0, SELL_SLOT_X, SELL_SLOT_Y, playerInventory.player, this::isSellDialogEnabled, scope));
        this.addStandardInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
    }

    // Client-side constructor (from IMenuTypeExtension extra data). Scope is irrelevant on the client.
    public TradeHubMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.TRADE_HUB.get(), containerId);
        this.serverPlayerOwner = playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.scope = Stock.DEFAULT_SCOPE;

        this.addSlot(new SellSlot(sellContainer, 0, SELL_SLOT_X, SELL_SLOT_Y, playerInventory.player, this::isSellDialogEnabled, scope));
        this.addStandardInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
    }

    private static String resolveScope(Level level, BlockPos hubPos) {
        // Check all six adjacent faces for a NetworkConnectorBlock with a non-empty name.
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = hubPos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof NetworkConnectorBlock) {
                if (level.getBlockEntity(neighborPos) instanceof NetworkConnectorBlockEntity connector) {
                    String name = connector.getNetworkName();
                    if (!name.isEmpty()) {
                        return "network:" + name;
                    }
                }
            }
        }
        // No connector found — this hub is a local market.
        return Stock.localScope(level, hubPos);
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

    public ItemStack getStagedSellItem() {
        return this.slots.get(SELL_SLOT_INDEX).getItem();
    }

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
            long currentEpoch = Stock.getEpoch(server, scope);
            if (currentEpoch != lastSyncedStockEpoch) {
                lastSyncedStockEpoch = currentEpoch;
                sendCatalogResult(lastCatalogQuery);
            }
        }

        ItemStack staged = sellContainer.getItem(0);
        if (sellDialogEnabled && !staged.isEmpty()) {
            long currentStock = Stock.getStock(server, scope, staged.getItem());
            if (currentStock != lastSyncedSellStock) {
                lastSyncedSellStock = currentStock;
                PacketDistributor.sendToPlayer(serverPlayerOwner, new SellPreviewPayload(currentStock));
            }
        } else {
            lastSyncedSellStock = Long.MIN_VALUE;
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            returnStagedItem(sp);
        }
    }

    private void returnStagedItem(ServerPlayer player) {
        ItemStack staged = sellContainer.getItem(0);
        if (staged.isEmpty()) {
            return;
        }
        sellContainer.setItem(0, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(staged);
    }

    public boolean isSellDialogEnabled() {
        return sellDialogEnabled;
    }

    public void handleSellDialogModeChange(boolean enabled) {
        this.sellDialogEnabled = enabled;
        if (!enabled && serverPlayerOwner != null) {
            returnStagedItem(serverPlayerOwner);
        }
    }

    public void handleSellDecision(SellDecisionPayload payload) {
        if (serverPlayerOwner == null) {
            return;
        }
        ItemStack staged = sellContainer.getItem(0);
        if (staged.isEmpty()) {
            return;
        }
        if (payload.confirm()) {
            sellContainer.setItem(0, ItemStack.EMPTY);
            TradeService.sell(serverPlayerOwner, staged, scope);
        } else {
            returnStagedItem(serverPlayerOwner);
        }
    }

    public void handleCatalogQuery(CatalogQueryPayload query) {
        if (serverPlayerOwner == null) {
            return;
        }
        this.lastCatalogQuery = query;
        this.lastSyncedStockEpoch = Stock.getEpoch(serverPlayerOwner.level().getServer(), scope);
        sendCatalogResult(query);
    }

    public void handleBuyRequest(BuyRequestPayload request) {
        if (serverPlayerOwner == null) {
            return;
        }
        TradeService.BuyOutcome outcome = TradeService.buy(serverPlayerOwner, request.item(), request.quantity(), request.quoteHash(), scope);
        PacketDistributor.sendToPlayer(serverPlayerOwner, new BuyResultPayload(outcome.success(), outcome.messageKey()));
    }

    private void sendCatalogResult(CatalogQueryPayload query) {
        MinecraftServer server = serverPlayerOwner.level().getServer();
        var entries = TradeService.queryCatalog(server, query.search(), query.sortKey(), query.ascending(), scope);
        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        PacketDistributor.sendToPlayer(serverPlayerOwner, new CatalogResultPayload(entries, feePercent));
    }

    public long getClientBalance() {
        return clientBalance;
    }

    public void setClientBalance(long balance) {
        this.clientBalance = balance;
    }

    public long getClientSellPreviewStock() {
        return clientSellPreviewStock;
    }

    public void setClientSellPreviewStock(long stock) {
        this.clientSellPreviewStock = stock;
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
