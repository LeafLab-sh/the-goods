# The Goods — Implementation Plan

## Context

`docs/spec.md` and `docs/trade-hub-menu-design.md` are complete, implementation-ready design docs for a NeoForge 26.2 mod where item value is driven by server-wide stock levels. The codebase, however, is currently a **100% unmodified NeoForge MDK template** — only `TheGoods.java`, `TheGoodsClient.java`, and `Config.java` exist, and all three are just template boilerplate (example block/item/tab, example config). None of the spec's actual features exist: no Trade Hub block, no economy/`SavedData`, no commands, no menus/screens, no networking. `Config.java` also had two syntax-breaking artifacts (stray tokens) that predated this plan and were fixed as Step 0 below.

Since this is the user's first Minecraft mod, this plan sequences work into **7 milestones**, each ending in something playable via `./gradlew runClient` — not just backend code with no way to observe it. Order runs from simple/foundational NeoForge concepts (block registration, datagen) toward complex ones (custom networking, hand-rolled GUI widgets), so modding literacy builds progressively. Each milestone lists concrete files/classes, the NeoForge concepts involved, and an exact in-game test procedure.

Reference mods are named throughout for patterns the spec itself invokes (e.g. the catalog widget explicitly mirrors AE2/Refined Storage/JEI item-list UIs). Because the mcmodding-mcp mappings database doesn't cover MC 26.1/26.2, treat any method/class names below as **approximate** — verify exact signatures against current NeoForge source/docs during implementation, not from memory.

## Step 0 — Fix corruptions (done)

- `src/main/java/sh/leaflab/goods/Config.java`: removed a stray `a` token before `MAGIC_NUMBER = BUILDER`, and a stray `cove` token after `.comment("A list of items to log on common setup.")`.
- `CLAUDE.md`: removed a stray standalone `upda` line between the `docs/spec.md` reference and the `## Commands` header.
- Verified: `./gradlew build` succeeds.

## Milestone 1 — Trade Hub block: craftable, placeable, breakable

Replace the example block/item scaffolding with a real Trade Hub block. No economy logic yet — right-click just sends a placeholder chat message. This is a low-risk repeat of the registration pattern already in `TheGoods.java`, good for building confidence before harder milestones.

**Files:**
- `sh.leaflab.goods.block.TradeHubBlock` (extends `Block`) — interact handler sends a placeholder chat message.
- `sh.leaflab.goods.TheGoods` — remove `EXAMPLE_BLOCK`/`EXAMPLE_BLOCK_ITEM`/`EXAMPLE_ITEM`; add `TRADE_HUB` block + `DeferredItem<BlockItem>`; repoint the creative tab to it.
- `sh.leaflab.goods.datagen.DataGenerators` (`GatherDataEvent` listener on `modEventBus`), `ModBlockStateProvider`, `ModRecipeProvider` (hollow 3x3 stick ring → 1 Trade Hub), `ModLootTableProvider` (`dropSelf`).
- `src/main/resources/assets/thegoods/lang/en_us.json` — replace example keys with `block.thegoods.trade_hub` + real tab title.
- A placeholder 16x16 texture PNG under `src/main/resources/assets/thegoods/textures/block/` (datagen only writes JSON referencing it; you still hand-author/borrow the image).

**NeoForge concepts:** `DeferredRegister`/`DeferredHolder` (repeat from template), `BlockBehaviour.Properties`, `GatherDataEvent`, `BlockStateProvider`/`RecipeProvider`/`LootTableProvider`, `ExistingFileHelper`, `CreativeModeTab` display items.

**Test:** `./gradlew runData`, inspect generated JSON under `src/generated/resources/`. Then `./gradlew runClient`: find the Trade Hub in its creative tab, place it, right-click for the chat message, break it in survival and confirm self-drop, craft one via the stick-ring recipe at a crafting table.

## Milestone 2 — Persistent balances + admin commands

First `SavedData` usage, first Brigadier command tree, first real persistence test.

**Files:**
- `sh.leaflab.goods.economy.Currency` — fixed-point representation: a `long` counting units of `10^-10` (`SCALE = 10_000_000_000L`), matching the spec's own "~±922M headroom" note (`Long.MAX_VALUE / 1e10 ≈ 922,337,203.68`). Full-precision display must build the string via integer division/modulo, never round-trip through `double`.
- `sh.leaflab.goods.economy.EconomyData` (extends `SavedData`) — `Map<UUID, Long> balances`, explicit `DATA_VERSION` int tag from day one, `setDirty()` + immediate flush save on every mutation (don't wait for autosave).
- `sh.leaflab.goods.economy.Economy` — facade exposing `getBalance`/`give`/`take` (floors at 0)/`reset`.
- `sh.leaflab.goods.command.GoodsCommand` — `RegisterCommandsEvent` on `NeoForge.EVENT_BUS`; `balance` (any player), `give`/`take`/`reset` (op 4).

**Gotcha — server-wide, not per-dimension:** `ServerLevel#getDataStorage()` is per-dimension. Always fetch the economy `SavedData` from one canonical level (conventionally `server.overworld().getDataStorage()`), never from `player.level()`, or you'll silently get N independent economies.

**NeoForge concepts:** `SavedData`/`SavedData.Factory` (API shape may be Codec-based in current MC — verify against live source), Brigadier `Commands.literal`/`.then`/`.requires`, custom decimal-to-fixed-point argument parsing (vanilla's numeric argument types are `double`/`float`, not fixed-point).

**Test:** `/goods balance` → `0.0000000000`. `/goods give <you> 100` → balance `100.0000000000`. Save-and-quit, reload the same world, `/goods balance` still `100.0000000000` (proves `SavedData` persistence). `/goods reset <you>` → back to 0. `/goods take <you> 50` at 0 balance → stays at 0.

## Milestone 3 — Pay and request commands

Social commands, clickable chat buttons, request persistence.

**Files:**
- `sh.leaflab.goods.economy.TradeRequest` — sender/target UUID + amount, stored in `SavedData` keyed by sender+target pair (new request overwrites old — no TTL logic needed, requests never expire).
- `sh.leaflab.goods.command.GoodsCommand` extended with `pay`, `request`, `request accept|deny|cancel|list`.
- PlayerName resolution via server `GameProfileCache` (works for offline players; clean command failure on unknown profile).
- Chat construction with clickable Accept/Deny using current `ClickEvent` API (verify current shape — this has changed across MC versions from a two-arg `Action`+`String` constructor to sealed-interface-style records).

**Gotcha:** Accept must re-validate the sender's balance **at resolution time**, not at request-creation time (balance may have dropped since).

**Reference mod:** **TpaPlusPlus** (`SuperRicky14/TpaPlusPlus` on GitHub, MIT, Forge/NeoForge/Fabric via Architectury) — look at how it builds teleport-request chat messages with `[Accept]`/`[Deny]` run-command click events and tracks one pending request per sender/target pair. Structurally the same pattern this spec wants.

**Test:** two-client (or client+server) test: `/goods pay <p2> 25` transfers immediately with no confirm, and fully rejects (no partial) on insufficient balance. `/goods request 25 <p2>` produces a clickable chat message on p2's side; clicking Accept updates both balances. A second request from the same sender/target replaces the first. Test `request cancel` and `request list` (both directions). Restart the server with a pending request and confirm `request list` still shows it.

## Milestone 4 — Stock economy engine (sell via right-click, no GUI yet)

Implements the actual value math and stock storage, observable through the simplest interaction (right-click sells the whole held stack) before tackling GUI complexity.

**Files:**
- `sh.leaflab.goods.economy.Currency` extended — `sellValue(stockBefore, qty)` / `buyCost(stockBefore, qty)` using `StrictMath.log1p` exactly as specified: sell = `log1p(k/(j+1.0)) / ln2`, buy = `-log1p(-k/(j+1.0)) / ln2`, with `ln2 = StrictMath.log(2.0)` as a `static final double`. Payouts round down, charges round up — round the fee-adjusted total **once**, not per intermediate step (getting this backwards reopens the wash-trading exploit the spec explicitly closes).
- `sh.leaflab.goods.economy.StockData` — `Map<ItemKey, Long>`, 64-bit counters, saturating add (compare-sign-bits overflow check, clamp rather than throw/wrap).
- `sh.leaflab.goods.economy.ItemEligibility` — shared validator: `getMaxStackSize() > 1`, component patch is empty (default state only — check via patch emptiness, not by enumerating individual component types, or a new component type later reopens the anvil-rename exploit), allow/deny-list config check.
- `sh.leaflab.goods.economy.TradeService` — single `sell(ServerPlayer, ItemStack)` entry point, reused later by `SellSlot` (M5) and the buy packet handler (M6) — write once, don't reimplement.
- `TradeHubBlock` interact handler now calls `TradeService.sell(...)` instead of the M1 placeholder.
- `Config.java` gets its real settings: `CurrencyName`, `TransactionFeePercent` (0-100, buys only), `ItemDenyList`, `ItemAllowList` — replacing the remaining template values.

**Keep `Currency` free of server-only types** (`ServerLevel`, `SavedData`) so it's safely callable unmodified from client-side buy-preview code in M6.

**Test:** sell a fresh item (`j=0`) and confirm payout matches `log1p(k/1)/ln2`, floor-rounded to 10 decimals. Sell more of the same item, confirm diminishing per-unit value. Try a renamed/enchanted item — rejected, stays in hand. Try a non-stackable item (shulker box) — rejected. Add an item to the deny-list via config, confirm new deposits rejected but existing stock can still be drained to 0. Restart the world, confirm stock persisted.

## Milestone 5 — Real GUI: Sell Slot + live balance sync

First custom `AbstractContainerMenu`/`AbstractContainerScreen`, first custom networking packet, first `Slot` subclass. The M4 right-click-to-sell behavior is retired in favor of opening this menu.

**Files:**
- `sh.leaflab.goods.menu.TradeHubMenu` (extends `AbstractContainerMenu`) — one `SellSlot` + vanilla player inventory. `MenuType` registered via a `sh.leaflab.goods.registry.ModMenuTypes` `DeferredRegister`.
- `sh.leaflab.goods.menu.SellSlot` (extends `Slot`) — `mayPlace` calls `ItemEligibility`; processes on **insert** (verify exact hook — `mayPlace`+`setChanged` vs. `onTake` vs. `set`/`setByPlayer` override — picking the wrong one is a common source of "shift-click doesn't repeat" bugs), then empties itself so repeated shift-clicks work. Test shift-click specifically — it routes through `AbstractContainerMenu#quickMoveStack`, a separate path from drag-and-drop.
- `sh.leaflab.goods.client.gui.TradeHubScreen` (extends `AbstractContainerScreen<TradeHubMenu>`) — sell slot, inventory, balance readout. Registered via `MenuScreens.register` in `TheGoodsClient`.
- `sh.leaflab.goods.network.BalanceSyncPayload` + `sh.leaflab.goods.network.NetworkHandler` — `CustomPacketPayload`/`StreamCodec` via `RegisterPayloadHandlersEvent` (verify exact 26.2 event/registrar shape — this API has had naming churn). Sent server→client on balance change while menu is open.
- `TradeHubBlock` interact handler now opens the menu instead of selling directly.

**Test:** right-click Trade Hub, screen opens with empty sell slot + balance label matching `/goods balance`. Drag an eligible stack into the slot — processes immediately, slot empties, balance updates on-screen without closing/reopening (proves the sync packet works). Shift-click a stack, confirm each click sells one increment (not batched). Try an ineligible item — stays put. Close mid-drag with a staged cursor item — normal vanilla cursor-return behavior.

## Milestone 6 — Catalog widget + full buying flow

The hardest milestone: hand-rolled non-`Slot` item grid, full packet suite, quote integrity.

**Files:**
- Packets: `CatalogQueryPayload` (C→S: search/sort/scroll), `CatalogResultPayload` (S→C: `{itemId, stock, unitPrice, quoteHash}` page), `BuyRequestPayload` (C→S: itemId+qty+echoed quoteHash), `StockUpdatePayload` (S→C, coalesced **once per server tick** across all open menus — needs a `ServerTickEvent.Post` listener aggregating diffs, not a packet per transaction).
- `sh.leaflab.goods.economy.QuoteHash` — short hash of `itemId+stock+unitPrice+quoteEpoch`; distinguishes stale-quote (hash matches a real prior quote → reject+refresh) from forged/replayed (hash matches nothing → reject+log as suspicious).
- `sh.leaflab.goods.client.gui.CatalogWidget` — search box, server-sorted Name/Price/Stock × asc/desc, scrollable grid excluding 0-stock items, **manual click hit-testing** (mouse coords → grid cell index — no `Slot` machinery does this for you; budget real time here, this is genuinely fiddly geometry code).
- `sh.leaflab.goods.client.gui.BuyDialog` — +/-/max buttons, qty clamped to `min(affordable-with-fee, available-stock)`, receipt breakdown computed by **calling the same shared `Currency` class** used server-side (not a reimplementation — any divergence breaks the spec's "preview never disagrees with server charge" guarantee).
- `sh.leaflab.goods.economy.TradeService#buy(...)` — validates itemId/qty (registry membership, allow/deny, `0 < qty <= stock`) **before** any pricing math; re-derives price from live stock regardless of client's claim; atomic deduct/decrement/give; full rejection on any failure.

**Gotcha — atomicity:** the server is single-threaded for game-state mutation, but `CustomPacketPayload` handlers can run off the main thread depending on registration. Explicitly hop to the main thread (`context.enqueueWork(...)` or 26.2 equivalent) before touching stock/balance in the buy handler — once synchronous on the main thread, check→compute→commit is atomic for free.

**Reference mods:**
- **Applied Energistics 2** (`AppliedEnergistics/Applied-Energistics-2`, GPL) — go straight to the ME terminal screen's item-grid rendering and scroll/click hit-testing (search their source tree for the terminal screen package; exact class names shift by release). This is the exact pattern the spec's own design doc names.
- **JEI** (`mezz/JustEnoughItems`, MIT) — simpler fallback reference: their ingredient list panel's layout/pagination and click-to-ingredient lookup, a flatter widget than AE2's terminal.

**Test:** catalog shows only stock>0 items; search filters; sort modes reorder (cursor drift during scroll is accepted per spec, don't chase it). Buy Dialog receipt matches `unitPrice × qty` + fee exactly, cross-checked against `/goods balance`. Buy at exact affordable max — doesn't overcharge. Buy more than stock — rejected. Two clients buying the same scarce item near-simultaneously — no duplication/negative stock. Full inventory — buy rejects before charging (balance unchanged). Stale/forged quote — rejected/refreshed, not silently repriced.

## Milestone 7 — Metrics, Sell Dialog toggle, edge-case hardening

Last spec surface plus formal GameTest coverage.

**Files:**
- `GoodsCommand` — add `metrics` (op 2): distinct item types, total stock value (`Σ log2(n+1)`), lifetime fees (running counter incremented per fee-bearing buy, tracked then discarded per spec — confirm it never becomes spendable), total circulation (`Σ balances`), top-N most/least valuable. Values >1000 use K/M/G/T/P/E/Z/Y abbreviation, **floored**, matching the `formatDouble` method from **InfiniteStorageCell**'s `InfinityCellItem` class (named directly in the spec — use it as the literal reference, don't re-derive the tier logic). `/goods balance` stays full-precision everywhere.
- `sh.leaflab.goods.client.gui.SellDialog` + a client-side-only toggle — switches Quick Sell into stage-then-confirm mode; Cancel/close-with-staged-item returns it like vanilla container close.
- `sh.leaflab.goods.gametest` package — `@GameTestHolder` tests covering the spec's own enumerated test-plan bullets: Sell Slot correctness, ineligible-item rejection, staged-item return on close, insufficient balance/space full rejection, concurrent buy/sell atomicity, forged quoteHash rejection, invalid qty rejected before pricing math runs, fee boundaries (0%/100%), catalog sort + 0-stock exclusion.

**Test:** `/goods metrics` after some trading — sane numbers, K/M/G formatting above 1000 while `/goods balance` stays full-precision. Toggle Sell Dialog on — quick-sell now stages instead of processing immediately; close with staged item returns it. Set fee to 0% and 100% via the config screen — buy charges correct at both boundaries. `./gradlew runGameTestServer` passes — first automated (non-manual) verification in the project.

## Verification

Each milestone above has its own concrete in-game test procedure via `./gradlew runClient` (or `runGameTestServer` for M7). Additionally, after Step 0 and after each milestone, run `./gradlew build` to confirm the project compiles cleanly before moving on.
