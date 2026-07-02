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

// The single entry point for both trade directions — reused by the Sell Slot GUI (Milestone 5) and the catalog
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

    // Buying does NOT run ItemEligibility's allow/deny-list check — per spec, a denylisted item with existing
    // stock must still be purchasable down to 0, only new deposits (selling) are blocked. Stock > 0 is the only
    // real precondition; a default-component check isn't needed either since every stack that ever entered stock
    // was already validated as default-component at sell time.
    public static BuyOutcome buy(ServerPlayer player, Identifier itemId, long quantity, int quoteHash) {
        if (quantity <= 0 || quantity > Integer.MAX_VALUE) {
            return fail(player, "commands.thegoods.buy.invalid_quantity");
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return fail(player, "commands.thegoods.buy.unknown_item");
        }

        MinecraftServer server = player.level().getServer();
        long stockBefore = Stock.getStock(server, item);
        if (quantity > stockBefore) {
            return fail(player, "commands.thegoods.buy.insufficient_stock");
        }

        long epoch = Stock.getEpoch(server);
        if (QuoteHash.of(itemId, stockBefore, epoch) != quoteHash) {
            return fail(player, "commands.thegoods.buy.stale_quote");
        }

        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        long cost = Currency.buyCostWithFee(stockBefore, quantity, feePercent);
        if (Economy.getBalance(server, player.getUUID()) < cost) {
            return fail(player, "commands.thegoods.buy.insufficient_balance");
        }

        if (!hasInventoryRoom(player.getInventory(), item, quantity)) {
            return fail(player, "commands.thegoods.buy.inventory_full");
        }

        Economy.take(server, player.getUUID(), cost);
        Stock.debit(server, item, quantity);

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

    // Returns the full filtered+sorted result set rather than a page — the client holds it and scrolls locally
    // (see CatalogWidget), matching Refined Storage's client-side grid scrolling instead of requesting a new page
    // per scroll tick. Re-sent whenever search/sort changes or the stock epoch advances (TradeHubMenu), not per
    // frame, so this isn't on any hot path.
    public static List<CatalogEntry> queryCatalog(MinecraftServer server, String search, String sortKey, boolean ascending) {
        Map<Item, Long> stock = Stock.positiveStock(server);
        long epoch = Stock.getEpoch(server);
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

    // Conservative and read-only: only counts guaranteed-empty slots and existing same-item default-component
    // stacks with room, never mutates anything. May slightly underestimate room in edge cases, but never
    // overestimates, so it can never lead to a partial give after currency has already been charged.
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
