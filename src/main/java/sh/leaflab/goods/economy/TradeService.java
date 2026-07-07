package sh.leaflab.goods.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.Config;
import sh.leaflab.goods.network.CatalogEntry;

// The single entry point for both trade directions — reused by the Sell Slot GUI and the catalog
// buy packet handler, so the eligibility/value/persistence logic is written once, not per surface.
// All operations require a stock scope string (see StockData) that identifies which stock pool to
// read/write — determined by TradeHubMenu based on whether an adjacent Network Connector exists.
public final class TradeService {
    private TradeService() {
    }

    public static boolean sell(ServerPlayer player, ItemStack stack, String scope) {
        if (!ItemEligibility.isEligible(stack)) {
            player.sendSystemMessage(Component.translatable("block.thegoods.trade_hub.not_eligible"));
            return false;
        }

        MinecraftServer server = player.level().getServer();
        Item item = stack.getItem();
        long quantity = stack.getCount();

        long stockBefore = Stock.getStock(server, scope, item);
        long payout = Currency.sellValue(stockBefore, quantity);

        Economy.give(server, player.getUUID(), payout);
        Stock.credit(server, scope, item, quantity);

        player.sendSystemMessage(Component.translatable(
                "block.thegoods.trade_hub.sold", quantity, stack.getHoverName(), Currency.format(payout)));
        return true;
    }

    // Buying does NOT run ItemEligibility's allow/deny-list check — per spec, a denylisted item with existing
    // stock must still be purchasable down to 0, only new deposits (selling) are blocked. Stock > 0 is the only
    // real precondition; a default-component check isn't needed either since every stack that ever entered stock
    // was already validated as default-component at sell time.
    public static BuyOutcome buy(ServerPlayer player, Identifier itemId, long quantity, int quoteHash, String scope) {
        if (quantity <= 0 || quantity > Integer.MAX_VALUE) {
            return fail(player, "commands.thegoods.buy.invalid_quantity");
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return fail(player, "commands.thegoods.buy.unknown_item");
        }

        MinecraftServer server = player.level().getServer();
        long stockBefore = Stock.getStock(server, scope, item);
        if (quantity > stockBefore) {
            return fail(player, "commands.thegoods.buy.insufficient_stock");
        }

        long epoch = Stock.getEpoch(server, scope);
        if (QuoteHash.of(itemId, stockBefore, epoch) != quoteHash) {
            return fail(player, "commands.thegoods.buy.stale_quote");
        }

        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        double rawCost = Currency.buyRawCost(stockBefore, quantity);
        long cost = Currency.ceilToFixedPoint(rawCost * (1.0 + feePercent / 100.0));
        if (Economy.getBalance(server, player.getUUID()) < cost) {
            return fail(player, "commands.thegoods.buy.insufficient_balance");
        }

        if (!hasInventoryRoom(player.getInventory(), item, quantity)) {
            return fail(player, "commands.thegoods.buy.inventory_full");
        }

        long feeCollected = cost - Currency.ceilToFixedPoint(rawCost);

        Economy.take(server, player.getUUID(), cost);
        if (feeCollected > 0) {
            Economy.addLifetimeFees(server, feeCollected);
        }
        Stock.debit(server, scope, item, quantity);

        long remaining = quantity;
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int give = (int) Math.min(remaining, maxStackSize);
            player.getInventory().add(new ItemStack(item, give));
            remaining -= give;
        }

        player.sendSystemMessage(Component.translatable(
                "block.thegoods.trade_hub.bought", quantity, new ItemStack(item).getHoverName(), Currency.format(cost)));
        return BuyOutcome.ok();
    }

    private static BuyOutcome fail(ServerPlayer player, String messageKey) {
        player.sendSystemMessage(Component.translatable(messageKey));
        return BuyOutcome.failure(messageKey);
    }

    public static List<CatalogEntry> queryCatalog(MinecraftServer server, String search, String sortKey, boolean ascending, String scope) {
        Map<Item, Long> stock = Stock.positiveStock(server, scope);
        long epoch = Stock.getEpoch(server, scope);
        String needle = search.toLowerCase(Locale.ROOT);

        List<Map.Entry<Item, Long>> filtered = new ArrayList<>();
        for (Map.Entry<Item, Long> entry : stock.entrySet()) {
            if (needle.isEmpty() || itemDisplayName(entry.getKey()).toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(entry);
            }
        }

        Comparator<Map.Entry<Item, Long>> comparator = switch (sortKey) {
            case "price" -> Comparator.comparingDouble(e -> Currency.buyRawCost(e.getValue(), 1));
            case "stock" -> Comparator.comparingLong(Map.Entry::getValue);
            default -> Comparator.comparing(e -> itemDisplayName(e.getKey()), String.CASE_INSENSITIVE_ORDER);
        };
        filtered.sort(ascending ? comparator : comparator.reversed());

        List<CatalogEntry> entries = new ArrayList<>(filtered.size());
        for (Map.Entry<Item, Long> entry : filtered) {
            Identifier id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            entries.add(new CatalogEntry(id, entry.getValue(), QuoteHash.of(id, entry.getValue(), epoch)));
        }
        return entries;
    }

    private static String itemDisplayName(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }

    private static boolean hasInventoryRoom(Inventory inventory, Item item, long quantity) {
        long remaining = quantity;
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty()) {
                remaining -= maxStackSize;
            } else if (existing.is(item) && existing.isComponentsPatchEmpty() && existing.getCount() < maxStackSize) {
                remaining -= maxStackSize - existing.getCount();
            }
        }
        return remaining <= 0;
    }

    public record BuyOutcome(boolean success, String messageKey) {
        public static BuyOutcome ok() {
            return new BuyOutcome(true, "commands.thegoods.buy.success");
        }

        public static BuyOutcome failure(String messageKey) {
            return new BuyOutcome(false, messageKey);
        }
    }
}
