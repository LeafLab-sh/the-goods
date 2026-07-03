# Repo Review Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Act on the high- and medium-priority findings from an independent repo review (see conversation this plan came from) — fix real code duplication/perf issues, close spec-mandated test coverage gaps, and bring `docs/trade-hub-menu-design.md`, `docs/description.md`, `README.md` back in sync with the actual implementation. Low-priority/cosmetic findings (dead `TEMPLATE_LICENSE.txt`, `CLAUDE.md` typo, tooltip comment) are explicitly out of scope per user decision.

**Architecture:** No new subsystems. Every task is a targeted, independently-committable change to an existing file: extract one shared helper, fix two hot-path inefficiencies, add five new automated tests (one JUnit, four+ GameTest — exact behaviors covered per-task below), rewrite two doc sections, sync two doc command tables, and replace duplicated CI setup steps with a local composite action.

**Tech Stack:** Java 25, NeoForge 26.2.0.7-beta, JUnit 5 (`src/test/java`), NeoForge GameTest (`src/main/java/.../gametest`), GitHub Actions.

## Global Constraints

- `StrictMath`, never `Math`, for any log/log1p call touching currency — bit-identical determinism (see `docs/spec.md` Value Calculation).
- New GameTests each use a dedicated, never-reused vanilla item, per `CLAUDE.md`'s guidance on avoiding collisions with `run/config/thegoods-common.toml` gitignored dev state.
- `./gradlew spotlessApply` before every commit that touches `.java` files (import order); `./gradlew build` must pass before every commit.
- Never fabricate a URL. Where a doc needs a real link this plan doesn't have (none currently), leave a note rather than inventing one.

---

## File Structure

- Modify: `src/main/java/sh/leaflab/goods/economy/Economy.java`, `Stock.java` — dedup SavedData access.
- Create: `src/main/java/sh/leaflab/goods/economy/SavedDataAccess.java` — the extracted shared helper.
- Modify: `src/main/java/sh/leaflab/goods/economy/TradeService.java` — avoid double `log1p` in fee accounting.
- Modify: `src/main/java/sh/leaflab/goods/command/GoodsCommand.java` — fix unqualified-type inconsistency.
- Modify: `src/main/java/sh/leaflab/goods/client/gui/CatalogCellWidget.java`, `CatalogWidget.java` — reuse cell widgets instead of reallocating on every scroll tick.
- Modify: `src/test/java/sh/leaflab/goods/economy/CurrencyTest.java` — wash-trading non-profitability test.
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java` — five new GameTests (fee boundaries, request-accept re-validation, metrics data, give/take/reset, sell-interleaved-with-buy).
- Modify: `docs/trade-hub-menu-design.md` — rewrite Architecture/Buying Flow/Networking sections to match the M6 restyle.
- Modify: `docs/description.md`, `README.md` — sync command tables, fix stale "Coming soon" section, remove placeholder link.
- Create: `.github/actions/setup-build-env/action.yml` — composite action for the duplicated checkout/JDK/Gradle setup.
- Modify: `.github/workflows/build-and-test.yml`, `.github/workflows/release.yml` — use the composite action.
- Modify: `CLAUDE.md` — one-line note about the new composite action, so the CI/CD section stays accurate.

---

### Task 1: Extract shared SavedData access helper

**Files:**
- Create: `src/main/java/sh/leaflab/goods/economy/SavedDataAccess.java`
- Modify: `src/main/java/sh/leaflab/goods/economy/Economy.java`
- Modify: `src/main/java/sh/leaflab/goods/economy/Stock.java`

**Interfaces:**
- Produces: `SavedDataAccess.get(MinecraftServer server, SavedDataType<T> type)` returning `T`, and `SavedDataAccess.flush(MinecraftServer server)` — both package-private, used only by `Economy` and `Stock`.

- [ ] **Step 1: Create the shared helper**

```java
package sh.leaflab.goods.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

// Shared by Economy and Stock: both are server-wide SavedData facades that must always read from the overworld's
// storage, never from whatever dimension the caller happens to be in (or there'd be one economy/stock ledger per
// dimension instead of one for the whole server), and must flush immediately after every mutation rather than
// waiting for autosave (see docs/spec.md).
final class SavedDataAccess {
    private SavedDataAccess() {
    }

    static <T extends SavedData> T get(MinecraftServer server, SavedDataType<T> type) {
        return dataStorage(server).computeIfAbsent(type);
    }

    static void flush(MinecraftServer server) {
        dataStorage(server).saveAndJoin();
    }

    private static SavedDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}
```

- [ ] **Step 2: Update `Economy.java` to use it**

Replace the file's private `data`/`dataStorage`/`flush` methods (currently lines 83-95) with:

```java
    // Always fetched from the overworld, never from whatever dimension the caller happens to be in, so there is
    // exactly one economy for the whole server rather than one per dimension.
    private static EconomyData data(MinecraftServer server) {
        return SavedDataAccess.get(server, EconomyData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
```

Remove the now-unused `import net.minecraft.world.level.storage.SavedDataStorage;` at the top of the file.

- [ ] **Step 3: Update `Stock.java` to use it**

Replace the file's private `data`/`dataStorage`/`flush` methods (currently lines 39-50) with:

```java
    // Always fetched from the overworld, matching Economy — one stock ledger for the whole server, not per dimension.
    private static StockData data(MinecraftServer server) {
        return SavedDataAccess.get(server, StockData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
```

Remove the now-unused `import net.minecraft.world.level.storage.SavedDataStorage;` at the top of the file.

- [ ] **Step 4: Build and verify**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL — this is a pure refactor, no behavior change, so `./gradlew test` and existing GameTests must still pass unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/sh/leaflab/goods/economy/SavedDataAccess.java src/main/java/sh/leaflab/goods/economy/Economy.java src/main/java/sh/leaflab/goods/economy/Stock.java
git commit -m "Extract shared SavedData access helper for Economy/Stock"
```

---

### Task 2: Avoid a redundant log1p call in buy fee accounting

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/economy/TradeService.java:78-96`

**Interfaces:**
- Consumes: `Currency.buyRawCost(long stockBefore, long quantity): double`, `Currency.ceilToFixedPoint(double rawValue): long` (both already public, unchanged).

- [ ] **Step 1: Replace the fee/cost computation**

Current code (lines 78-96):

```java
        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        long cost = Currency.buyCostWithFee(stockBefore, quantity, feePercent);
        if (Economy.getBalance(server, player.getUUID()) < cost) {
            return fail(player, "commands.thegoods.buy.insufficient_balance");
        }

        if (!hasInventoryRoom(player.getInventory(), item, quantity)) {
            return fail(player, "commands.thegoods.buy.inventory_full");
        }

        // The fee portion is the difference between what was actually charged and what a 0%-fee purchase of the
        // same quantity at the same stock level would have cost — both ceil-rounded independently, but that's
        // fine here since this is a lifetime reporting counter (/goods metrics), not itself a transacted amount.
        long feeCollected = cost - Currency.buyCost(stockBefore, quantity);
```

Replace with:

```java
        int feePercent = Config.TRANSACTION_FEE_PERCENT.get();
        // buyRawCost (the expensive StrictMath.log1p call) computed once and reused for both the fee-adjusted
        // charge and the fee-free baseline below — buyCostWithFee/buyCost would each redo it independently.
        double rawCost = Currency.buyRawCost(stockBefore, quantity);
        long cost = Currency.ceilToFixedPoint(rawCost * (1.0 + feePercent / 100.0));
        if (Economy.getBalance(server, player.getUUID()) < cost) {
            return fail(player, "commands.thegoods.buy.insufficient_balance");
        }

        if (!hasInventoryRoom(player.getInventory(), item, quantity)) {
            return fail(player, "commands.thegoods.buy.inventory_full");
        }

        // The fee portion is the difference between what was actually charged and what a 0%-fee purchase of the
        // same quantity at the same stock level would have cost — both ceil-rounded independently from the same
        // raw value, but that's fine here since this is a lifetime reporting counter (/goods metrics), not itself
        // a transacted amount.
        long feeCollected = cost - Currency.ceilToFixedPoint(rawCost);
```

- [ ] **Step 2: Verify the fee-boundary unit tests still pass**

Run: `./gradlew test`
Expected: `CurrencyTest`'s `buyCostWithFeeAppliesFeeToRawValueBeforeRounding`, `zeroFeeMatchesPlainBuyCost`, and `hundredPercentFeeDoublesTheRawCostBeforeRounding` still pass unchanged — `Currency.buyCostWithFee`/`buyCost` themselves aren't touched, only `TradeService`'s internal call pattern.

- [ ] **Step 3: Run integration tests**

Run: `./gradlew runGameTestServer`
Expected: all existing tests pass (`buyRejectedOnInsufficientBalance`, `buyingLastUnitIsAtomic`, `forgedQuoteHashRejected`, `invalidQuantityRejectedBeforePricing` exercise `TradeService.buy` directly and must produce identical charges to before).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/sh/leaflab/goods/economy/TradeService.java
git commit -m "Avoid recomputing buyRawCost when accounting for collected fees"
```

---

### Task 3: Fix fully-qualified Supplier type in GoodsCommand

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/command/GoodsCommand.java`

- [ ] **Step 1: Add the import**

Add to the `java.util.*` import block near the top of the file (after `import java.util.UUID;`):

```java
import java.util.function.Supplier;
```

- [ ] **Step 2: Drop the qualification**

Change line 370 from:

```java
    private static void notifyIfOnline(MinecraftServer server, UUID playerId, java.util.function.Supplier<Component> message) {
```

to:

```java
    private static void notifyIfOnline(MinecraftServer server, UUID playerId, Supplier<Component> message) {
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL — pure import cleanup, no behavior change.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/sh/leaflab/goods/command/GoodsCommand.java
git commit -m "Import Supplier in GoodsCommand instead of fully-qualifying it"
```

---

### Task 4: Reuse catalog cell widgets instead of reallocating on every scroll tick

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/client/gui/CatalogCellWidget.java`
- Modify: `src/main/java/sh/leaflab/goods/client/gui/CatalogWidget.java:213-230`

**Interfaces:**
- Produces: `CatalogCellWidget#update(int x, int y, CatalogEntry entry): void` — repositions and rebinds an existing cell widget in place.

- [ ] **Step 1: Make `CatalogCellWidget` reusable**

Change the `entry`/`displayStack` fields from `final` and move their assignment into a new `update` method, called from the constructor:

```java
public class CatalogCellWidget extends AbstractWidget {
    private CatalogEntry entry;
    private ItemStack displayStack;
    private final Font font;
    private final BiConsumer<CatalogEntry, Boolean> onSelect;

    public CatalogCellWidget(int x, int y, CatalogEntry entry, Font font, BiConsumer<CatalogEntry, Boolean> onSelect) {
        super(x, y, 18, 18, Component.empty());
        this.font = font;
        this.onSelect = onSelect;
        update(x, y, entry);
    }

    /** Repositions this cell and rebinds it to a different catalog entry, so CatalogWidget can reuse cell widgets
     * across scroll ticks and query results instead of reallocating the whole visible grid every time. */
    public void update(int x, int y, CatalogEntry entry) {
        this.setX(x);
        this.setY(y);
        this.entry = entry;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
        this.setTooltip(Tooltip.create(displayStack.getHoverName()));
    }
```

The rest of the class (`entry()`, `extractWidgetRenderState`, `renderAmount`, `onClick`, `updateWidgetNarration`) is unchanged.

- [ ] **Step 2: Update `CatalogWidget#rebuildVisibleCells` to reuse instead of reallocate**

Replace (current lines 213-230):

```java
    private void rebuildVisibleCells() {
        for (CatalogCellWidget cell : cells) {
            removeWidget.accept(cell);
        }
        cells.clear();

        int firstRow = (int) scrollbar.getOffset();
        int start = firstRow * GRID_COLS;
        int end = Math.min(start + GRID_ROWS * GRID_COLS, allEntries.size());
        for (int i = start; i < end; i++) {
            int indexInView = i - start;
            int col = indexInView % GRID_COLS;
            int row = indexInView / GRID_COLS;
            CatalogCellWidget cell = new CatalogCellWidget(x + col * CELL_SIZE, gridY() + row * CELL_SIZE, allEntries.get(i), font, onSelect);
            cells.add(cell);
            addWidget.accept(cell);
        }
    }
```

with:

```java
    // Reuses existing cell widgets in place (repositioned/rebound to a new entry) rather than reallocating the
    // whole visible grid on every scroll-wheel tick and every query result — this runs continuously while the
    // mouse wheel is held, and each cell carries an ItemStack + Tooltip that's cheap but not free to recreate.
    private void rebuildVisibleCells() {
        int firstRow = (int) scrollbar.getOffset();
        int start = firstRow * GRID_COLS;
        int end = Math.min(start + GRID_ROWS * GRID_COLS, allEntries.size());
        int visibleCount = end - start;

        while (cells.size() > visibleCount) {
            removeWidget.accept(cells.remove(cells.size() - 1));
        }

        for (int i = 0; i < visibleCount; i++) {
            int indexInView = i;
            int col = indexInView % GRID_COLS;
            int row = indexInView / GRID_COLS;
            int cellX = x + col * CELL_SIZE;
            int cellY = gridY() + row * CELL_SIZE;
            CatalogEntry entry = allEntries.get(start + i);
            if (i < cells.size()) {
                cells.get(i).update(cellX, cellY, entry);
            } else {
                CatalogCellWidget cell = new CatalogCellWidget(cellX, cellY, entry, font, onSelect);
                cells.add(cell);
                addWidget.accept(cell);
            }
        }
    }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification in-game**

Run: `./gradlew runClient`, sell a handful of different items into stock, open the Trade Hub, scroll the catalog with the mouse wheel and the scrollbar, and confirm: cells still show the right item/stock/tooltip at every scroll position, hovering/clicking still works, and no visual flicker or stale icons appear. Also change sort mode/direction and confirm the grid still updates correctly.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/sh/leaflab/goods/client/gui/CatalogCellWidget.java src/main/java/sh/leaflab/goods/client/gui/CatalogWidget.java
git commit -m "Reuse catalog cell widgets across scroll ticks instead of reallocating"
```

---

### Task 5: Add a wash-trading non-profitability test

**Files:**
- Modify: `src/test/java/sh/leaflab/goods/economy/CurrencyTest.java`

This is pure `Currency` math (no Minecraft dependency), so it belongs in the JUnit suite per `CLAUDE.md`, not GameTest — closes the gap on spec.md's headline invariant: "a buy-then-sell round trip never profitable — break-even at best."

- [ ] **Step 1: Write the test**

Add to `CurrencyTest.java` (anywhere after the existing buy/sell tests):

```java
    @Test
    void buyThenSellRoundTripIsNeverProfitable() {
        // spec.md: "This makes a buy-then-sell round trip never profitable — break-even at best, at exact
        // integer log boundaries where there's nothing to round." Buy 5 into a stock of 100, then immediately
        // sell the same 5 back — floor(sell) must never exceed ceil(buy) for the same quantity/stock window.
        long stockBefore = 100;
        long quantity = 5;

        long buyCost = Currency.buyCost(stockBefore, quantity);
        long stockAfterBuy = stockBefore - quantity;
        long sellPayout = Currency.sellValue(stockAfterBuy, quantity);

        assertTrue(sellPayout <= buyCost, "selling back what was just bought must never pay out more than it cost");
    }

    @Test
    void sellThenBuyRoundTripIsNeverProfitable() {
        // The other direction: sell 5 into a stock of 100, then immediately buy the same 5 back.
        long stockBefore = 100;
        long quantity = 5;

        long sellPayout = Currency.sellValue(stockBefore, quantity);
        long stockAfterSell = stockBefore + quantity;
        long buyCost = Currency.buyCost(stockAfterSell, quantity);

        assertTrue(buyCost >= sellPayout, "buying back what was just sold must never cost less than it paid out");
    }
```

Add `import static org.junit.jupiter.api.Assertions.assertTrue;` to the existing static-import block at the top of the file (alongside the existing `assertEquals`/`assertThrows` imports).

- [ ] **Step 2: Run the tests**

Run: `./gradlew test --tests "sh.leaflab.goods.economy.CurrencyTest"`
Expected: both new tests PASS (the rounding policy — floor on sell, ceil on buy — guarantees this holds at every stock level, per `docs/spec.md` Rounding Strategy).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/sh/leaflab/goods/economy/CurrencyTest.java
git commit -m "Add wash-trading non-profitability tests for buy/sell round trips"
```

---

### Task 6: Add the missing fee-boundary GameTest

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java`

`docs/implementation-plan.md`'s Milestone 7 section already describes nine tests including "0%/100% fee boundaries" — the code only has eight. Adding this test makes that doc line accurate again; no doc edit needed.

**Interfaces:**
- Consumes: `Config.TRANSACTION_FEE_PERCENT` (`ModConfigSpec.IntValue`, has `.get(): int` and `.set(Integer): void`), `Currency.buyCost(long, long): long`, `Currency.buyCostWithFee(long, long, int): long` (all already public).

- [ ] **Step 1: Add the import**

Add near the top of `EconomyGameTests.java`, alongside the other `sh.leaflab.goods.*` imports:

```java
import sh.leaflab.goods.Config;
```

- [ ] **Step 2: Register the new test**

Add a new `DeferredHolder` constant alongside the existing eight (after `CATALOG_EXCLUDES_ZERO_STOCK_AND_SORTS`):

```java
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BUY_COST_HONORS_FEE_BOUNDARIES =
            register("buy_cost_honors_fee_boundaries", EconomyGameTests::buyCostHonorsFeeBoundaries);
```

Add `BUY_COST_HONORS_FEE_BOUNDARIES` to the `List.of(...)` inside `register(RegisterGameTestsEvent event)`.

- [ ] **Step 3: Write the test method**

```java
    // Exercises docs/spec.md's TransactionFeePercent boundaries (0-100 inclusive): at 0% the buyer pays exactly
    // the unrounded buy cost, and at 100% the fee doubles the raw cost before the single ceil-rounding step
    // (never rounded twice — see Currency's own Javadoc on buyCostWithFee). Restores the original config value
    // in a finally block so this test can't leak a changed fee into any test that runs after it in the batch.
    private static void buyCostHonorsFeeBoundaries(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = testPlayer(helper, "fee-boundary");
        Item item = Items.GLOWSTONE_DUST;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        int originalFeePercent = Config.TRANSACTION_FEE_PERCENT.get();

        TradeService.sell(player, new ItemStack(item, 10));
        Economy.give(server, player.getUUID(), Currency.parseExact("1000000"));

        try {
            Config.TRANSACTION_FEE_PERCENT.set(0);
            long stockBeforeZeroFee = Stock.getStock(server, item);
            long balanceBeforeZeroFee = Economy.getBalance(server, player.getUUID());
            int zeroFeeQuoteHash = QuoteHash.of(itemId, stockBeforeZeroFee, Stock.getEpoch(server));
            TradeService.BuyOutcome zeroFeeOutcome = TradeService.buy(player, itemId, 1, zeroFeeQuoteHash);
            long zeroFeeCharge = balanceBeforeZeroFee - Economy.getBalance(server, player.getUUID());

            helper.assertTrue(zeroFeeOutcome.success(), "0% fee buy should succeed");
            helper.assertTrue(zeroFeeCharge == Currency.buyCost(stockBeforeZeroFee, 1), "0% fee should charge exactly the unrounded buy cost, no markup");

            Config.TRANSACTION_FEE_PERCENT.set(100);
            long stockBeforeFullFee = Stock.getStock(server, item);
            long balanceBeforeFullFee = Economy.getBalance(server, player.getUUID());
            int fullFeeQuoteHash = QuoteHash.of(itemId, stockBeforeFullFee, Stock.getEpoch(server));
            TradeService.BuyOutcome fullFeeOutcome = TradeService.buy(player, itemId, 1, fullFeeQuoteHash);
            long fullFeeCharge = balanceBeforeFullFee - Economy.getBalance(server, player.getUUID());

            helper.assertTrue(fullFeeOutcome.success(), "100% fee buy should succeed");
            helper.assertTrue(fullFeeCharge == Currency.buyCostWithFee(stockBeforeFullFee, 1, 100), "100% fee should charge the fee-adjusted, once-rounded cost");
        } finally {
            Config.TRANSACTION_FEE_PERCENT.set(originalFeePercent);
        }

        helper.succeed();
    }
```

- [ ] **Step 4: Build and run**

Run: `./gradlew spotlessApply build runGameTestServer`
Expected: BUILD SUCCESSFUL, and `thegoods:buy_cost_honors_fee_boundaries` reported as passed among 9 total economy tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java
git commit -m "Add missing fee-boundary GameTest (0%/100% TransactionFeePercent)"
```

---

### Task 7: Add a request-accept balance re-validation GameTest

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java`

Tests the invariant `GoodsCommand#requestAccept` depends on — re-validating the payer's balance at resolution time, not request-creation time — by driving the same `Economy` calls the command handler makes, matching this suite's existing pattern of testing the economy facade directly rather than dispatching Brigadier commands.

**Interfaces:**
- Consumes: `Economy.putRequest`, `Economy.findRequest`, `Economy.removeRequest`, `Economy.transfer`, `TradeRequest` record (`requester`, `payer`, `amount` accessors) — all already public in `sh.leaflab.goods.economy`.

- [ ] **Step 1: Add the import**

```java
import sh.leaflab.goods.economy.TradeRequest;
```

- [ ] **Step 2: Register the new test**

```java
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REQUEST_ACCEPT_REVALIDATES_BALANCE =
            register("request_accept_revalidates_balance", EconomyGameTests::requestAcceptRevalidatesBalance);
```

Add `REQUEST_ACCEPT_REVALIDATES_BALANCE` to the `List.of(...)` in `register(RegisterGameTestsEvent event)`.

- [ ] **Step 3: Write the test method**

```java
    // Exercises docs/spec.md's accept-time re-validation requirement: an Accept must re-check the payer's balance
    // at resolution time, not at request-creation time, since it may have dropped in between. Drives the same
    // Economy calls GoodsCommand#requestAccept makes (findRequest/getBalance/removeRequest/transfer) rather than
    // dispatching the slash command itself, matching this suite's existing pattern.
    private static void requestAcceptRevalidatesBalance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer requester = testPlayer(helper, "request-accept-requester");
        ServerPlayer payer = testPlayer(helper, "request-accept-payer");
        long requestAmount = Currency.parseExact("100");

        Economy.give(server, payer.getUUID(), requestAmount);
        Economy.putRequest(server, new TradeRequest(requester.getUUID(), payer.getUUID(), requestAmount));

        // Balance drops below the pending request's amount after the request was made but before it's accepted.
        Economy.take(server, payer.getUUID(), Currency.parseExact("50"));

        TradeRequest pending = Economy.findRequest(server, requester.getUUID(), payer.getUUID()).orElse(null);
        helper.assertTrue(pending != null, "the pending request should still be found at resolution time");
        boolean sufficientBalance = Economy.getBalance(server, payer.getUUID()) >= pending.amount();
        helper.assertTrue(!sufficientBalance, "payer's balance should now be insufficient for the pending request");
        helper.assertTrue(Economy.findRequest(server, requester.getUUID(), payer.getUUID()).isPresent(),
                "an insufficient-balance accept must leave the request pending, not silently consume it");

        // Top the payer back up past the amount and confirm acceptance succeeds and transfers correctly.
        Economy.give(server, payer.getUUID(), Currency.parseExact("50"));
        long payerBalanceBefore = Economy.getBalance(server, payer.getUUID());
        long requesterBalanceBefore = Economy.getBalance(server, requester.getUUID());

        Economy.removeRequest(server, requester.getUUID(), payer.getUUID());
        Economy.transfer(server, payer.getUUID(), requester.getUUID(), pending.amount());

        helper.assertTrue(Economy.getBalance(server, payer.getUUID()) == payerBalanceBefore - pending.amount(), "accepted transfer should debit the payer by exactly the request amount");
        helper.assertTrue(Economy.getBalance(server, requester.getUUID()) == requesterBalanceBefore + pending.amount(), "accepted transfer should credit the requester by exactly the request amount");
        helper.assertTrue(Economy.findRequest(server, requester.getUUID(), payer.getUUID()).isEmpty(), "the request should be gone once accepted");
        helper.succeed();
    }
```

- [ ] **Step 4: Build and run**

Run: `./gradlew spotlessApply build runGameTestServer`
Expected: BUILD SUCCESSFUL, `thegoods:request_accept_revalidates_balance` passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java
git commit -m "Add GameTest for pay/request accept-time balance re-validation"
```

---

### Task 8: Add a /goods metrics data GameTest

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java`

**Interfaces:**
- Consumes: `Stock.positiveStock`, `Currency.stockValue`, `Economy.totalCirculation`, `Currency.buyRawCost` — all already public.

- [ ] **Step 1: Add the import**

```java
import java.util.Map;
```

(alongside the existing `java.util.List`, `java.util.UUID`, `java.util.function.Consumer` imports.)

- [ ] **Step 2: Register the new test**

```java
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> METRICS_DATA_AGGREGATES_CORRECTLY =
            register("metrics_data_aggregates_correctly", EconomyGameTests::metricsDataAggregatesCorrectly);
```

Add `METRICS_DATA_AGGREGATES_CORRECTLY` to the `List.of(...)` in `register(RegisterGameTestsEvent event)`.

- [ ] **Step 3: Write the test method**

```java
    // Exercises the data GoodsCommand#metrics reports (docs/spec.md Metrics): total stock value (sum of
    // log2(n+1)), total currency in circulation, and that per-unit price moves opposite to stock — the item with
    // less stock prices higher, so "most valuable" is the low-stock end of a stock-ascending sort. Drives
    // Stock/Economy/Currency directly, the same data metrics() itself reads, rather than parsing chat output.
    private static void metricsDataAggregatesCorrectly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = testPlayer(helper, "metrics-seller");
        Item scarce = Items.GHAST_TEAR;
        Item plentiful = Items.PUFFERFISH;

        TradeService.sell(seller, new ItemStack(scarce, 1));
        TradeService.sell(seller, new ItemStack(plentiful, 50));
        Economy.give(server, seller.getUUID(), Currency.parseExact("1000"));

        Map<Item, Long> stock = Stock.positiveStock(server);
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
        helper.assertTrue(scarcePrice > plentifulPrice, "the item with less stock should price higher per unit — 'most valuable' is the low-stock end of the list");
        helper.succeed();
    }
```

- [ ] **Step 4: Build and run**

Run: `./gradlew spotlessApply build runGameTestServer`
Expected: BUILD SUCCESSFUL, `thegoods:metrics_data_aggregates_correctly` passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java
git commit -m "Add GameTest for /goods metrics underlying data"
```

---

### Task 9: Add a give/take/reset GameTest

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java`

**Interfaces:**
- Consumes: `Economy.give`, `Economy.take`, `Economy.reset`, `Economy.getBalance` — all already public.

- [ ] **Step 1: Register the new test**

```java
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GIVE_TAKE_RESET_MUTATE_BALANCES_CORRECTLY =
            register("give_take_reset_mutate_balances_correctly", EconomyGameTests::giveTakeResetMutateBalancesCorrectly);
```

Add `GIVE_TAKE_RESET_MUTATE_BALANCES_CORRECTLY` to the `List.of(...)` in `register(RegisterGameTestsEvent event)`.

- [ ] **Step 2: Write the test method**

```java
    // Exercises /goods give|take|reset's underlying Economy mutations: give/take/reset all persist balance
    // changes (docs/spec.md), and take floors at 0 rather than driving a balance negative.
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
```

- [ ] **Step 3: Build and run**

Run: `./gradlew spotlessApply build runGameTestServer`
Expected: BUILD SUCCESSFUL, `thegoods:give_take_reset_mutate_balances_correctly` passes.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java
git commit -m "Add GameTest for give/take/reset balance mutations"
```

---

### Task 10: Add a sell-interleaved-with-buy GameTest

**Files:**
- Modify: `src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java`

Only buyer-vs-buyer atomicity is currently tested (`buyingLastUnitIsAtomic`). This adds coverage for a sell landing between a buyer taking a quote and confirming it — the quote must go stale, not silently reprice.

**Interfaces:**
- Consumes: `TradeService.sell`, `TradeService.buy`, `QuoteHash.of`, `Stock.getStock`, `Stock.getEpoch` — all already public.

- [ ] **Step 1: Register the new test**

```java
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SELL_INTERLEAVED_WITH_BUY_KEEPS_STOCK_CONSISTENT =
            register("sell_interleaved_with_buy_keeps_stock_consistent", EconomyGameTests::sellInterleavedWithBuyKeepsStockConsistent);
```

Add `SELL_INTERLEAVED_WITH_BUY_KEEPS_STOCK_CONSISTENT` to the `List.of(...)` in `register(RegisterGameTestsEvent event)`.

- [ ] **Step 2: Write the test method**

```java
    // Exercises stock consistency when a sell lands between a buyer taking a quote and confirming it — sequential
    // within GameTest's single-threaded model (see buyingLastUnitIsAtomic's own note on what "concurrent" means
    // here), but confirms the buyer's now-stale quote is rejected rather than silently repriced, and that a fresh
    // quote taken afterward succeeds with the stock ledger exactly reflecting both sells and the one buy.
    private static void sellInterleavedWithBuyKeepsStockConsistent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = testPlayer(helper, "interleave-seller");
        ServerPlayer buyer = testPlayer(helper, "interleave-buyer");
        Item item = Items.NAUTILUS_SHELL;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        TradeService.sell(seller, new ItemStack(item, 5));
        long stockAfterFirstSell = Stock.getStock(server, item);
        int quoteHash = QuoteHash.of(itemId, stockAfterFirstSell, Stock.getEpoch(server));
        Economy.give(server, buyer.getUUID(), Currency.parseExact("1000000"));

        // Seller adds more stock after the buyer's quote was taken but before the buyer confirms.
        TradeService.sell(seller, new ItemStack(item, 3));
        TradeService.BuyOutcome staleOutcome = TradeService.buy(buyer, itemId, 2, quoteHash);
        helper.assertTrue(!staleOutcome.success(), "a quote taken before an interleaving sell should be rejected as stale, not silently repriced");

        long stockBeforeBuy = Stock.getStock(server, item);
        int freshQuoteHash = QuoteHash.of(itemId, stockBeforeBuy, Stock.getEpoch(server));
        TradeService.BuyOutcome freshOutcome = TradeService.buy(buyer, itemId, 2, freshQuoteHash);

        helper.assertTrue(freshOutcome.success(), "a fresh quote taken after the interleaving sell should succeed");
        helper.assertTrue(Stock.getStock(server, item) == stockBeforeBuy - 2, "final stock should reflect exactly the net of both sells and the one successful buy");
        helper.succeed();
    }
```

- [ ] **Step 3: Build and run**

Run: `./gradlew spotlessApply build runGameTestServer`
Expected: BUILD SUCCESSFUL, `thegoods:sell_interleaved_with_buy_keeps_stock_consistent` passes, and all 12 economy GameTests pass together.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/sh/leaflab/goods/gametest/EconomyGameTests.java
git commit -m "Add GameTest for a sell interleaving with a buyer's stale quote"
```

---

### Task 11: Rewrite trade-hub-menu-design.md to match the shipped M6 UI

**Files:**
- Modify: `docs/trade-hub-menu-design.md`

- [ ] **Step 1: Replace "Architecture & Screen Layout"**

Replace the entire section (from `## Architecture & Screen Layout` through the paragraph ending "...capped by `Slot` count.") with:

```markdown
## Architecture & Screen Layout

`TradeHubMenu` (`AbstractContainerMenu`) + `TradeHubScreen` (`AbstractContainerScreen`), restyled after Refined
Storage's grid screen (see the UI pass notes in `docs/implementation-plan.md` Milestone 6):

```
┌─────────────────────────────────────────┐
│  [search icon][search box___]   (title)  │
│┌ ┐ ┌───────────────────────────────────┐ │
│└ ┘ │  catalog icon grid (9x4, scrollable)│▐│
│┌ ┐ └───────────────────────────────────┘ │
│└ ┘                                        │
│  Sell         │  Buy                     │
│  [Sell Slot]  │  [item icon] name...      │
│  balance/price│  [-][+][Max]              │
│               │  qty / available          │
│               │  total cost               │
│               │  [Confirm]                │
│  [ player inventory 3x9 ]                 │
│  [ hotbar ]                                │
└─────────────────────────────────────────┘
```

- **Catalog widget** (top): custom-drawn, not `Slot`-backed. Search bar shares its row with the screen title;
  sort mode/direction are floating side buttons to the left of the panel (mirroring Refined Storage's
  `AbstractBaseScreen#addSideButton`); a 9-column scrollable icon grid (9 columns to match the imported Refined
  Storage `grid/row` sprite's native 162px width) with a real drag/wheel scrollbar (`ScrollbarWidget`), not
  pagination buttons. 0-stock items are excluded (reappear once sold in).
- **Sell side** (bottom-left): a "Sell" heading, the `SellSlot`, and — in Quick Sell mode — the balance readout
  below it, or — in Sell Dialog mode — the staged item's price above the balance instead (see Selling Flow).
- **Buy panel** (bottom-right): `BuyDialog`, an **always-visible embedded panel** (not a popup or overlay), with
  its buttons simply disabled when nothing's selected. Clicking a catalog cell opens it populated with that item;
  the item name can wrap to more than one line, and everything below it (+/-/Max, receipt, Confirm) shifts down
  to match (`BuyDialog#recomputeLayout`).
- **Divider**: a vertical sunken groove at the panel's horizontal midpoint separates the Sell and Buy halves.
- **Player inventory** (bottom): standard vanilla slots, drawn with the same imported Refined Storage row sprite
  as the catalog grid.

Rationale: real `Slot`s where real inventory mechanics are needed; the catalog is custom-rendered since global
stock is a counter, not physical items — same approach as AE2's terminal, Refined Storage, and JEI's item list.
Scales to any number of item types instead of being capped by `Slot` count.
```

- [ ] **Step 2: Replace "Buying Flow"**

Replace the entire `## Buying Flow` section with:

```markdown
## Buying Flow

1. Search box (debounced ~10 ticks) or a sort-mode/direction side-button click sends a `CatalogQueryPayload`
   (`search`, `sortKey`, `ascending`) to the server.
2. Server replies with a `CatalogResultPayload`: the **entire** filtered+sorted result set (not paginated) plus
   the current `TransactionFeePercent`. The client holds this list and scrolls through it locally by row offset —
   no per-scroll-tick round trip. Re-sent automatically whenever the server's stock epoch advances while the menu
   is open (`TradeHubMenu#broadcastChanges`, coalesced once per tick), not via a separate stock-update payload.
3. Clicking a catalog cell opens/updates the **Buy Dialog** (always visible, not an overlay) with that entry.
   `-`/`+` step by 1 normally, a full stack on Shift, 10 on Ctrl; Max jumps to the largest quantity affordable at
   the current fee (`BuyDialog#maxAffordable`, binary-searched since cost isn't linear in quantity).
   Shift-clicking a catalog cell directly adds a full stack to the current selection instead of just selecting it.
4. The dialog's receipt line shows the total cost via `Currency.buyCostWithFee(stock, quantity, feePercent)` — the
   exact same method and formula (`log1p`/`StrictMath`, per `spec.md` Value Calculation) the server uses to
   charge, so the preview can never disagree with the final charge by even the smallest unit.
5. **Confirm** sends a `BuyRequestPayload` (`item`, `quantity`, `quoteHash` echoed from the entry the dialog was
   opened with) — no client-computed price is trusted or transmitted.
6. Server (`TradeService#buy`) validates, in order: quantity in range, item known, quantity ≤ live stock, quote
   hash matches the current `itemId+stock+epoch` (else rejected as stale — see Networking), balance sufficient
   for the fee-adjusted cost, inventory has room — then atomically deducts currency, decrements stock, and gives
   the items. A `BuyResultPayload` (success + message key) lets the Buy Dialog react without parsing chat; the
   client's balance and catalog both refresh live via the same per-tick sync as everything else.
7. If any check fails, the **whole transaction is rejected** — never a partial buy. The dialog stays open on the
   same entry for the player to retry once a fresh catalog result arrives.
```

- [ ] **Step 3: Replace "Networking, Edge Cases & Testing"**

Replace the entire section with:

```markdown
## Networking, Edge Cases & Testing

**Payloads** (`sh.leaflab.goods.network`, all `record`s with a `StreamCodec.composite`/`.map` codec):
- `CatalogQueryPayload` (C→S): `search`, `sortKey`, `ascending`.
- `CatalogResultPayload` (S→C): the full filtered+sorted `List<CatalogEntry>` plus `feePercent` — no per-entry
  unit price field; the client derives price from `stock` via the same `Currency` methods the server uses, so it
  can never drift.
- `BuyRequestPayload` (C→S): `item` (Identifier), `quantity`, `quoteHash`.
- `BuyResultPayload` (S→C): `success`, `messageKey` — lets the Buy Dialog react to the outcome without parsing
  chat.
- `BalanceSyncPayload` (S→C): `balance`, sent whenever it changes while a `TradeHubMenu` is open.
- `SellPreviewPayload` (S→C): `stockBeforeSale`, sent while an item is staged in Sell Dialog mode.
- `SellDecisionPayload` (C→S): `confirm` — Confirm/Cancel on whatever's currently staged.
- `SetSellDialogModePayload` (C→S): `enabled` — the Quick Sell / Sell Dialog toggle; purely a UI preference, not
  a security boundary.
- There is **no separate stock-update payload**: `TradeHubMenu` remembers each player's last `CatalogQueryPayload`
  and re-answers it with a fresh `CatalogResultPayload` whenever the stock epoch changes, inside the same per-tick
  `broadcastChanges()` already used for balance sync — a deliberate simplification from this doc's original
  per-tick-batched `StockUpdatePacket` design (see `docs/implementation-plan.md` Milestone 6).

**Packet integrity:** `StreamCodec` rejects malformed payloads at decode time, and price is always
server-recomputed from live stock (Buying Flow step 6) — a forged `BuyRequestPayload` can't buy at an invented
price regardless. `QuoteHash.of(itemId, stock, epoch)` (`sh.leaflab.goods.economy.QuoteHash`) hashes
`itemId + stock + epoch` — no `unitPrice` field — and isn't itself a security boundary: it's a cheap way to tell
ordinary staleness (hash matches a real quote, stock just moved since — reject and the next catalog refresh
corrects it) apart from a forged/replayed hash (matches nothing the server ever computed for that item). Either
way, `itemId`/`quantity` are validated independently before any cost is computed: registry membership,
`0 < quantity ≤ currentStock`.

**Edge cases:**
- Full inventory on buy → reject before deducting currency (`TradeService#hasInventoryRoom`, checked last so a
  rejection here never leaves a partial charge).
- Stock/price changed between the catalog snapshot and Confirm → `quoteHash` mismatch, rejected and the dialog
  awaits the next catalog refresh rather than silently repricing.
- Sell Slot rejects an ineligible item → it never leaves the player's inventory/cursor, no special handling
  needed.
- Sort keys (price, stock) can change while a player is scrolling a sorted list, so an item may shift position
  across a page boundary. Accepted as normal shopping-list churn at this catalog's scale — not worth a
  stable-cursor mechanism.

**Testing:** `./gradlew test` runs JUnit unit tests for pure logic (`Currency`, no Minecraft dependencies);
`./gradlew runGameTestServer` runs `EconomyGameTests` for everything that needs a live server/SavedData/item
registry — see `CLAUDE.md` and `docs/implementation-plan.md` Milestone 7 for the current test list.
```

- [ ] **Step 4: Spot-check the rest of the doc**

Read through the unchanged `## Scope` and `## Selling Flow` sections and confirm they still match `SellSlot.java`/`SellDialog.java`/`TradeHubMenu.java` (they do — Selling Flow's Quick Sell/Sell Dialog description was already accurate; only Architecture/Buying Flow/Networking had drifted).

- [ ] **Step 5: Commit**

```bash
git add docs/trade-hub-menu-design.md
git commit -m "Sync trade-hub-menu-design.md with the shipped Milestone 6 UI"
```

---

### Task 12: Sync command tables and fix stale claims in README.md and docs/description.md

**Files:**
- Modify: `README.md`
- Modify: `docs/description.md`

Both docs' command tables are missing `/goods metrics`. `docs/description.md` additionally has a "Coming soon" section describing already-shipped features and a literal unfilled placeholder link.

- [ ] **Step 1: Add the missing `/goods metrics` row to README.md's command table**

In `README.md`, after the `/goods reset <player>` row, add:

```markdown
| `/goods metrics` | gamemaster | Server-wide economy stats: item types in stock, total stock value, currency in circulation, top/bottom valued items |
```

- [ ] **Step 2: Update docs/description.md's Commands list**

Replace:

```markdown
## Commands

- `/goods balance [player]` — check a balance
- `/goods pay <player> <amount>` — send currency directly
- `/goods request <amount> <player>` — ask another player to pay you, with an in-chat Accept/Deny prompt
- `/goods request accept|deny|cancel|list` — manage pending requests
- `/goods give|take|reset <player> [amount]` — admin balance management
```

with:

```markdown
## Commands

- `/goods balance [player]` — check a balance
- `/goods pay <player> <amount>` — send currency directly
- `/goods request <amount> <player>` — ask another player to pay you, with an in-chat Accept/Deny prompt
- `/goods request accept|deny|cancel|list` — manage pending requests
- `/goods give|take|reset <player> [amount]` — admin balance management
- `/goods metrics` — admin-only server-wide economy stats
```

- [ ] **Step 3: Fold shipped "Coming soon" features into Features, and drop the section**

Replace the `## Coming soon` section (and the "Server-configurable" feature bullet stays as-is) — add a new feature bullet after "A real currency" in the `## Features` section:

```markdown
**Server insights**
Admins get `/goods metrics` for a server-wide read on the economy — item types in stock, total stock value,
currency in circulation, and the top/bottom valued items. Sellers who want a confirm-before-selling step can
opt into Sell Dialog mode instead of the default instant Quick Sell.
```

Then delete the entire `## Coming soon` section:

```markdown
## Coming soon

Server-wide economy metrics (`/goods metrics`), an optional confirm-before-selling mode for the Sell Slot, and further UI polish are planned for an upcoming update.
```

- [ ] **Step 4: Remove the placeholder link**

Replace:

```markdown
---

*Found a bug or have a suggestion? [Link to your issue tracker / Discord / GitHub here.]*
```

with:

```markdown
---

*Found a bug or have a suggestion? Feel free to leave a comment below.*
```

(No real issue-tracker URL is available to this plan — a CurseForge listing's own comment section doesn't need one. If a real tracker/Discord link exists, replace this line with it directly rather than leaving the placeholder.)

- [ ] **Step 5: Review the diff for tone**

Read the full updated `docs/description.md` top to bottom and confirm it still reads as a coherent CurseForge-style pitch (not a changelog) — the goal is accuracy, not turning it into a second README.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/description.md
git commit -m "Sync command tables and fix stale claims in README.md and docs/description.md"
```

---

### Task 13: Replace duplicated CI setup steps with a composite action

**Files:**
- Create: `.github/actions/setup-build-env/action.yml`
- Modify: `.github/workflows/build-and-test.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `CLAUDE.md`

A composite action (not a reusable `workflow_call` workflow) is used deliberately: `release.yml`'s later steps need the jar `build/libs/*.jar` produced by the build step, which only stays available within the same job. Splitting the build into a separate `workflow_call`'d job would require adding artifact upload/download just to pass the jar across job boundaries — unnecessary complexity for what's actually just duplicated setup boilerplate. A composite action runs its steps inline within the calling job, so no job-boundary/artifact issue arises.

- [ ] **Step 1: Create the composite action**

```yaml
name: Setup build environment
description: Checkout, JDK 25, and Gradle — the common setup both CI workflows need before running Gradle tasks.

runs:
  using: composite
  steps:
    - name: Checkout repository
      uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
      with:
        fetch-depth: 0
        fetch-tags: true

    - name: Setup JDK 25
      uses: actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5.4.0
      with:
        java-version: '25'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
```

Save as `.github/actions/setup-build-env/action.yml`. This is a local composite action, not fetched from a registry, so `CLAUDE.md`'s SHA-pinning rule (which applies to third-party marketplace actions) doesn't apply to referencing it — only to the `actions/checkout`/`actions/setup-java`/`gradle/actions/setup-gradle` steps still pinned inside it.

- [ ] **Step 2: Update build-and-test.yml**

Replace the whole file with:

```yaml
name: Check

on: [push, pull_request]

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - name: Setup build environment
        uses: ./.github/actions/setup-build-env

      - name: Build & Unit Test
        run: ./gradlew build

      - name: Run Integration Tests
        run: ./gradlew runGameTestServer
```

- [ ] **Step 3: Update release.yml**

Replace the whole file with:

```yaml
name: Release

# Pushing a semver tag (e.g. v1.2.3) is the single source of truth for a release's version — the tag drives the
# build (mod_version is overridden to match it, see the Build step below), so there's never a chance of the
# release jar disagreeing with the tag it was published under.

on:
  push:
    tags:
      - "v*.*.*"

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: Setup build environment
        uses: ./.github/actions/setup-build-env

      - name: Derive mod version from tag
        id: version
        run: echo "mod_version=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"

      - name: Build & Unit Test
        run: ./gradlew build -Pmod_version=${{ steps.version.outputs.mod_version }}

      - name: Run Integration Tests
        run: ./gradlew runGameTestServer -Pmod_version=${{ steps.version.outputs.mod_version }}

      - name: Create GitHub Release
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh release create "${{ github.ref_name }}" \
            build/libs/*.jar \
            --title "${{ github.ref_name }}" \
            --generate-notes

      # Runs after the GitHub Release exists so it can reuse that release's title/changelog for CurseForge too.
      # loaders/game-versions/dependencies are read automatically from the jar's own neoforge.mods.toml — no
      # need to duplicate them here (see https://github.com/Kira-NT/mc-publish#-inputs for what's auto-resolved).
      - name: Publish to CurseForge
        uses: Kira-NT/mc-publish@995edadc13559a8b28d0b7e6571229f067ec7659 # v3.3.0
        with:
          curseforge-id: 1595080
          curseforge-token: ${{ secrets.CURSEFORGE_TOKEN }}
```

- [ ] **Step 4: Update CLAUDE.md's CI/CD section**

In `CLAUDE.md`, under `### CI/CD`, change:

```markdown
### CI/CD
- `.github/workflows/*.yml` — every action is pinned to a commit SHA (not a version tag), with a `# vX.Y.Z`
  comment alongside it; keep new/updated actions pinned the same way.
```

to:

```markdown
### CI/CD
- `.github/actions/setup-build-env/` — a local composite action (checkout + JDK 25 + Gradle setup) shared by both
  workflows below, so a SHA-pin bump only needs to happen in one place.
- `.github/workflows/*.yml` — every third-party action (inside the composite action or a workflow directly) is
  pinned to a commit SHA (not a version tag), with a `# vX.Y.Z` comment alongside it; keep new/updated actions
  pinned the same way.
```

- [ ] **Step 5: Verify locally**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (unaffected by the CI change, but confirms the working tree is still healthy before pushing a workflow change).

There's no local GitHub Actions runner in this repo, so the actual workflow execution can only be verified by pushing/opening a PR — call this out explicitly when handing this task back for review.

- [ ] **Step 6: Commit**

```bash
git add .github/actions/setup-build-env/action.yml .github/workflows/build-and-test.yml .github/workflows/release.yml CLAUDE.md
git commit -m "Extract duplicated CI checkout/JDK/Gradle setup into a composite action"
```

---

## Verification

After all 13 tasks: run `./gradlew spotlessCheck build test runGameTestServer` once from a clean tree and confirm everything passes together (12 GameTests total, all JUnit tests including the two new wash-trading tests). Push the branch (or open a PR) at least once before merging so `build-and-test.yml`'s actual CI run validates the composite-action change, since that can't be verified locally.
