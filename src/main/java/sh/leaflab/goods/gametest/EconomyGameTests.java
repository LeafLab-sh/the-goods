package sh.leaflab.goods.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.Config;
import sh.leaflab.goods.TheGoods;
import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.QuoteHash;
import sh.leaflab.goods.economy.Stock;
import sh.leaflab.goods.economy.TradeRequest;
import sh.leaflab.goods.economy.TradeService;
import sh.leaflab.goods.menu.TradeHubMenu;
import sh.leaflab.goods.network.CatalogEntry;

public final class EconomyGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, TheGoods.MODID);

    private static final int MAX_TICKS = 40;

    // All tests use a dedicated test scope so they never collide with each other or with dev state.
    private static final String TEST_SCOPE = "network:test";

    private EconomyGameTests() {
    }

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SELL_PAYS_OUT_AND_INCREMENTS_STOCK =
            register("sell_pays_out_and_increments_stock", EconomyGameTests::sellPaysOutAndIncrementsStock);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SELL_REJECTS_NON_DEFAULT_COMPONENTS =
            register("sell_rejects_non_default_components", EconomyGameTests::sellRejectsNonDefaultComponents);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SELL_DIALOG_STAGED_ITEM_RETURNED_ON_CLOSE =
            register("sell_dialog_staged_item_returned_on_close", EconomyGameTests::sellDialogStagedItemReturnedOnClose);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BUY_REJECTED_ON_INSUFFICIENT_BALANCE =
            register("buy_rejected_on_insufficient_balance", EconomyGameTests::buyRejectedOnInsufficientBalance);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BUYING_LAST_UNIT_IS_ATOMIC =
            register("buying_last_unit_is_atomic", EconomyGameTests::buyingLastUnitIsAtomic);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FORGED_QUOTE_HASH_REJECTED =
            register("forged_quote_hash_rejected", EconomyGameTests::forgedQuoteHashRejected);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> INVALID_QUANTITY_REJECTED_BEFORE_PRICING =
            register("invalid_quantity_rejected_before_pricing", EconomyGameTests::invalidQuantityRejectedBeforePricing);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CATALOG_EXCLUDES_ZERO_STOCK_AND_SORTS =
            register("catalog_excludes_zero_stock_and_sorts", EconomyGameTests::catalogExcludesZeroStockAndSorts);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BUY_COST_HONORS_FEE_BOUNDARIES =
            register("buy_cost_honors_fee_boundaries", EconomyGameTests::buyCostHonorsFeeBoundaries);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REQUEST_ACCEPT_REVALIDATES_BALANCE =
            register("request_accept_revalidates_balance", EconomyGameTests::requestAcceptRevalidatesBalance);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> METRICS_DATA_AGGREGATES_CORRECTLY =
            register("metrics_data_aggregates_correctly", EconomyGameTests::metricsDataAggregatesCorrectly);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GIVE_TAKE_RESET_MUTATE_BALANCES_CORRECTLY =
            register("give_take_reset_mutate_balances_correctly", EconomyGameTests::giveTakeResetMutateBalancesCorrectly);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SELL_INTERLEAVED_WITH_BUY_KEEPS_STOCK_CONSISTENT =
            register("sell_interleaved_with_buy_keeps_stock_consistent", EconomyGameTests::sellInterleavedWithBuyKeepsStockConsistent);

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> register(String name, Consumer<GameTestHelper> test) {
        return TEST_FUNCTIONS.register(name, () -> test);
    }

    public static void register(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(TheGoods.MODID, "economy"));
        Identifier emptyStructure = Identifier.withDefaultNamespace("empty");
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : List.of(
                SELL_PAYS_OUT_AND_INCREMENTS_STOCK, SELL_REJECTS_NON_DEFAULT_COMPONENTS, SELL_DIALOG_STAGED_ITEM_RETURNED_ON_CLOSE,
                BUY_REJECTED_ON_INSUFFICIENT_BALANCE, BUYING_LAST_UNIT_IS_ATOMIC, FORGED_QUOTE_HASH_REJECTED,
                INVALID_QUANTITY_REJECTED_BEFORE_PRICING, CATALOG_EXCLUDES_ZERO_STOCK_AND_SORTS, BUY_COST_HONORS_FEE_BOUNDARIES,
                REQUEST_ACCEPT_REVALIDATES_BALANCE, METRICS_DATA_AGGREGATES_CORRECTLY, GIVE_TAKE_RESET_MUTATE_BALANCES_CORRECTLY,
                SELL_INTERLEAVED_WITH_BUY_KEEPS_STOCK_CONSISTENT)) {
            event.registerTest(test.getId(), new FunctionGameTestInstance(
                    test.getKey(), new TestData<>(environment, emptyStructure, MAX_TICKS, 0, true)));
        }
    }

    private static void sellPaysOutAndIncrementsStock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "sell-payout");
        Item item = Items.RABBIT_FOOT;

        long stockBefore = Stock.getStock(server, TEST_SCOPE, item);
        long balanceBefore = Economy.getBalance(server, player.getUUID());
        long expectedPayout = Currency.sellValue(stockBefore, 5);

        boolean sold = TradeService.sell(player, new ItemStack(item, 5), TEST_SCOPE);

        helper.assertTrue(sold, "sell should succeed for a plain, eligible stack");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stockBefore + 5, "stock should increase by exactly the quantity sold");
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == balanceBefore + expectedPayout, "balance should increase by exactly the computed payout");
        helper.succeed();
    }

    private static void sellRejectsNonDefaultComponents(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "sell-ineligible");
        Item item = Items.IRON_INGOT;
        ItemStack renamed = new ItemStack(item, 1);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));

        long stockBefore = Stock.getStock(server, TEST_SCOPE, item);
        long balanceBefore = Economy.getBalance(server, player.getUUID());

        boolean sold = TradeService.sell(player, renamed, TEST_SCOPE);

        helper.assertTrue(!sold, "a renamed (non-default-component) stack should be rejected");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stockBefore, "stock should be unchanged after a rejected sell");
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == balanceBefore, "balance should be unchanged after a rejected sell");
        helper.succeed();
    }

    private static void sellDialogStagedItemReturnedOnClose(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper, "sell-dialog-close");
        Item item = Items.EMERALD;
        TradeHubMenu menu = new TradeHubMenu(1, player.getInventory());
        menu.handleSellDialogModeChange(true);

        menu.getSlot(0).setByPlayer(new ItemStack(item, 3), ItemStack.EMPTY);
        helper.assertTrue(!menu.getStagedSellItem().isEmpty(), "item should be staged, not sold immediately, in Sell Dialog mode");
        helper.assertTrue(countItem(player, item) == 0, "staged item shouldn't be in the player's inventory yet");

        menu.removed(player);

        helper.assertTrue(menu.getStagedSellItem().isEmpty(), "Sell Slot should be empty after the menu closes");
        helper.assertTrue(countItem(player, item) == 3, "closing with a staged item should return it to the player, not lose it");
        helper.succeed();
    }

    private static void buyRejectedOnInsufficientBalance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "buy-insufficient-balance");
        Item item = Items.GOLD_INGOT;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(player, new ItemStack(item, 10), TEST_SCOPE);
        Economy.reset(server, player.getUUID());
        long stock = Stock.getStock(server, TEST_SCOPE, item);
        int quoteHash = QuoteHash.of(itemId, stock, Stock.getEpoch(server, TEST_SCOPE));

        TradeService.BuyOutcome outcome = TradeService.buy(player, itemId, 1, quoteHash, TEST_SCOPE);

        helper.assertTrue(!outcome.success(), "buy should fail with a zero balance");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stock, "stock should be unchanged after a rejected buy");
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == 0, "balance should still be zero after a rejected buy");
        helper.succeed();
    }

    private static void buyingLastUnitIsAtomic(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = testPlayer(helper, "atomic-seller");
        Item item = Items.NETHERITE_INGOT;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(seller, new ItemStack(item, 1), TEST_SCOPE);
        long stock = Stock.getStock(server, TEST_SCOPE, item);
        int quoteHash = QuoteHash.of(itemId, stock, Stock.getEpoch(server, TEST_SCOPE));

        ServerPlayer buyerA = testPlayer(helper, "atomic-buyer-a");
        ServerPlayer buyerB = testPlayer(helper, "atomic-buyer-b");
        long plenty = Currency.parseExact("1000000");
        Economy.give(server, buyerA.getUUID(), plenty);
        Economy.give(server, buyerB.getUUID(), plenty);

        TradeService.BuyOutcome outcomeA = TradeService.buy(buyerA, itemId, stock, quoteHash, TEST_SCOPE);
        TradeService.BuyOutcome outcomeB = TradeService.buy(buyerB, itemId, stock, quoteHash, TEST_SCOPE);

        helper.assertTrue(outcomeA.success(), "the first buyer should successfully buy all remaining stock");
        helper.assertTrue(!outcomeB.success(), "the second buyer should be rejected — stock is already exhausted");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == 0, "stock should be exactly zero, never negative");
        helper.succeed();
    }

    private static void forgedQuoteHashRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "forged-quote");
        Item item = Items.LAPIS_LAZULI;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(player, new ItemStack(item, 5), TEST_SCOPE);
        Economy.give(server, player.getUUID(), Currency.parseExact("1000000"));
        long stock = Stock.getStock(server, TEST_SCOPE, item);

        int forgedHash = ~QuoteHash.of(itemId, stock, Stock.getEpoch(server, TEST_SCOPE));
        TradeService.BuyOutcome outcome = TradeService.buy(player, itemId, 1, forgedHash, TEST_SCOPE);

        helper.assertTrue(!outcome.success(), "a quoteHash that was never issued should be rejected, not silently repriced");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stock, "stock should be unchanged after a rejected buy");
        helper.succeed();
    }

    private static void invalidQuantityRejectedBeforePricing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "invalid-quantity");
        Item item = Items.REDSTONE;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(player, new ItemStack(item, 5), TEST_SCOPE);
        Economy.give(server, player.getUUID(), Currency.parseExact("1000000"));
        long balanceBefore = Economy.getBalance(server, player.getUUID());
        long stockBefore = Stock.getStock(server, TEST_SCOPE, item);

        TradeService.BuyOutcome outcome = TradeService.buy(player, itemId, 0, 0, TEST_SCOPE);

        helper.assertTrue(!outcome.success(), "a zero quantity should be rejected");
        helper.assertTrue(outcome.messageKey().equals("commands.thegoods.buy.invalid_quantity"), "should fail specifically on the invalid-quantity check, before any pricing math");
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == balanceBefore, "balance should be untouched — pricing math never ran");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stockBefore, "stock should be untouched");
        helper.succeed();
    }

    private static void catalogExcludesZeroStockAndSorts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "catalog-sort");
        Item low = Items.CLAY_BALL;
        Item mid = Items.FLINT;
        Item high = Items.QUARTZ;
        Item neverSold = Items.PRISMARINE_SHARD;

        TradeService.sell(player, new ItemStack(low, 1), TEST_SCOPE);
        TradeService.sell(player, new ItemStack(mid, 5), TEST_SCOPE);
        TradeService.sell(player, new ItemStack(high, 20), TEST_SCOPE);

        List<CatalogEntry> results = TradeService.queryCatalog(server, "", "stock", true, TEST_SCOPE);

        Identifier neverSoldId = BuiltInRegistries.ITEM.getKey(neverSold);
        helper.assertTrue(results.stream().noneMatch(e -> e.item().equals(neverSoldId)), "an item with 0 stock should never appear in the catalog");

        int lowIdx = indexOf(results, BuiltInRegistries.ITEM.getKey(low));
        int midIdx = indexOf(results, BuiltInRegistries.ITEM.getKey(mid));
        int highIdx = indexOf(results, BuiltInRegistries.ITEM.getKey(high));
        helper.assertTrue(lowIdx >= 0 && midIdx >= 0 && highIdx >= 0, "all three stocked items should appear in the catalog");
        helper.assertTrue(lowIdx < midIdx && midIdx < highIdx, "ascending stock sort should order low < mid < high stock");
        helper.succeed();
    }

    private static void buyCostHonorsFeeBoundaries(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "fee-boundary");
        Item item = Items.GLOWSTONE_DUST;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        int originalFeePercent = Config.TRANSACTION_FEE_PERCENT.get();

        TradeService.sell(player, new ItemStack(item, 10), TEST_SCOPE);
        Economy.give(server, player.getUUID(), Currency.parseExact("1000000"));

        try {
            Config.TRANSACTION_FEE_PERCENT.set(0);
            long stockBeforeZeroFee = Stock.getStock(server, TEST_SCOPE, item);
            long balanceBeforeZeroFee = Economy.getBalance(server, player.getUUID());
            int zeroFeeQuoteHash = QuoteHash.of(itemId, stockBeforeZeroFee, Stock.getEpoch(server, TEST_SCOPE));
            TradeService.BuyOutcome zeroFeeOutcome = TradeService.buy(player, itemId, 1, zeroFeeQuoteHash, TEST_SCOPE);
            long zeroFeeCharge = balanceBeforeZeroFee - Economy.getBalance(server, player.getUUID());

            helper.assertTrue(zeroFeeOutcome.success(), "0% fee buy should succeed");
            helper.assertTrue(zeroFeeCharge == Currency.buyCost(stockBeforeZeroFee, 1), "0% fee should charge exactly the unrounded buy cost, no markup");

            Config.TRANSACTION_FEE_PERCENT.set(100);
            long stockBeforeFullFee = Stock.getStock(server, TEST_SCOPE, item);
            long balanceBeforeFullFee = Economy.getBalance(server, player.getUUID());
            int fullFeeQuoteHash = QuoteHash.of(itemId, stockBeforeFullFee, Stock.getEpoch(server, TEST_SCOPE));
            TradeService.BuyOutcome fullFeeOutcome = TradeService.buy(player, itemId, 1, fullFeeQuoteHash, TEST_SCOPE);
            long fullFeeCharge = balanceBeforeFullFee - Economy.getBalance(server, player.getUUID());

            helper.assertTrue(fullFeeOutcome.success(), "100% fee buy should succeed");
            helper.assertTrue(fullFeeCharge == Currency.buyCostWithFee(stockBeforeFullFee, 1, 100), "100% fee should charge the fee-adjusted, once-rounded cost");
        } finally {
            Config.TRANSACTION_FEE_PERCENT.set(originalFeePercent);
        }

        helper.succeed();
    }

    private static void requestAcceptRevalidatesBalance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer requester = testPlayer(helper, "request-accept-requester");
        ServerPlayer payer = testPlayer(helper, "request-accept-payer");
        long requestAmount = Currency.parseExact("100");

        Economy.give(server, payer.getUUID(), requestAmount);
        Economy.putRequest(server, new TradeRequest(requester.getUUID(), payer.getUUID(), requestAmount));

        Economy.take(server, payer.getUUID(), Currency.parseExact("50"));

        TradeRequest pending = Economy.findRequest(server, requester.getUUID(), payer.getUUID()).orElse(null);
        helper.assertTrue(pending != null, "the pending request should still be found at resolution time");

        boolean acceptedWithInsufficientBalance = Economy.acceptRequest(server, pending);
        helper.assertTrue(!acceptedWithInsufficientBalance, "the real guard must reject an accept when the payer's balance is now insufficient");
        helper.assertTrue(Economy.findRequest(server, requester.getUUID(), payer.getUUID()).isPresent(),
                "an insufficient-balance accept must leave the request pending, not silently consume it");

        Economy.give(server, payer.getUUID(), Currency.parseExact("50"));
        long payerBalanceBefore = Economy.getBalance(server, payer.getUUID());
        long requesterBalanceBefore = Economy.getBalance(server, requester.getUUID());

        boolean acceptedNow = Economy.acceptRequest(server, pending);

        helper.assertTrue(acceptedNow, "the real guard must accept once the payer's balance is sufficient again");
        helper.assertTrue(Economy.getBalance(server, payer.getUUID()) == payerBalanceBefore - pending.amount(), "accepted transfer should debit the payer by exactly the request amount");
        helper.assertTrue(Economy.getBalance(server, requester.getUUID()) == requesterBalanceBefore + pending.amount(), "accepted transfer should credit the requester by exactly the request amount");
        helper.assertTrue(Economy.findRequest(server, requester.getUUID(), payer.getUUID()).isEmpty(), "the request should be gone once accepted");
        helper.succeed();
    }

    private static void metricsDataAggregatesCorrectly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = testPlayer(helper, "metrics-seller");
        Item scarce = Items.GHAST_TEAR;
        Item plentiful = Items.PUFFERFISH;

        TradeService.sell(seller, new ItemStack(scarce, 1), TEST_SCOPE);
        TradeService.sell(seller, new ItemStack(plentiful, 50), TEST_SCOPE);
        Economy.give(server, seller.getUUID(), Currency.parseExact("1000"));

        Map<Item, Long> stock = Stock.positiveStock(server, TEST_SCOPE);
        helper.assertTrue(stock.containsKey(scarce) && stock.containsKey(plentiful), "both traded items should appear in positive stock");

        double totalStockValue = 0;
        for (long n : stock.values()) {
            totalStockValue += Currency.stockValue(n);
        }
        helper.assertTrue(totalStockValue > 0, "total stock value should be positive once anything is in stock");

        long circulation = Economy.totalCirculation(server);
        helper.assertTrue(circulation >= Currency.parseExact("1000"), "circulation should include the currency just given to the seller");

        double scarcePrice = Currency.buyRawCost(stock.get(scarce), 1);
        double plentifulPrice = Currency.buyRawCost(stock.get(plentiful), 1);
        helper.assertTrue(scarcePrice > plentifulPrice, "the item with less stock should price higher per unit");
        helper.succeed();
    }

    private static void giveTakeResetMutateBalancesCorrectly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "admin-balance-ops");

        Economy.give(server, player.getUUID(), Currency.parseExact("100"));
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == Currency.parseExact("100"), "give should credit exactly the given amount to a zero balance");

        Economy.take(server, player.getUUID(), Currency.parseExact("30"));
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == Currency.parseExact("70"), "take should debit exactly the taken amount");

        Economy.take(server, player.getUUID(), Currency.parseExact("1000"));
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == 0, "take should floor at 0 rather than going negative");

        Economy.give(server, player.getUUID(), Currency.parseExact("50"));
        Economy.reset(server, player.getUUID());
        helper.assertTrue(Economy.getBalance(server, player.getUUID()) == 0, "reset should zero the balance regardless of what it was before");
        helper.succeed();
    }

    private static void sellInterleavedWithBuyKeepsStockConsistent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = testPlayer(helper, "interleave-seller");
        ServerPlayer buyer = testPlayer(helper, "interleave-buyer");
        Item item = Items.NAUTILUS_SHELL;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(seller, new ItemStack(item, 5), TEST_SCOPE);
        long stockAfterFirstSell = Stock.getStock(server, TEST_SCOPE, item);
        int quoteHash = QuoteHash.of(itemId, stockAfterFirstSell, Stock.getEpoch(server, TEST_SCOPE));
        Economy.give(server, buyer.getUUID(), Currency.parseExact("1000000"));

        TradeService.sell(seller, new ItemStack(item, 3), TEST_SCOPE);
        TradeService.BuyOutcome staleOutcome = TradeService.buy(buyer, itemId, 2, quoteHash, TEST_SCOPE);
        helper.assertTrue(!staleOutcome.success(), "a quote taken before an interleaving sell should be rejected as stale, not silently repriced");

        long stockBeforeBuy = Stock.getStock(server, TEST_SCOPE, item);
        int freshQuoteHash = QuoteHash.of(itemId, stockBeforeBuy, Stock.getEpoch(server, TEST_SCOPE));
        TradeService.BuyOutcome freshOutcome = TradeService.buy(buyer, itemId, 2, freshQuoteHash, TEST_SCOPE);

        helper.assertTrue(freshOutcome.success(), "a fresh quote taken after the interleaving sell should succeed");
        helper.assertTrue(Stock.getStock(server, TEST_SCOPE, item) == stockBeforeBuy - 2, "final stock should reflect exactly the net of both sells and the one successful buy");
        helper.succeed();
    }

    private static ServerPlayer testPlayer(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static long countItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        long total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int indexOf(List<CatalogEntry> entries, Identifier itemId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).item().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }
}
