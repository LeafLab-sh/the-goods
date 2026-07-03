# Trade Hub Menu — Design

Implementation design for the Trade Hub block's player-facing GUI. See [`docs/spec.md`](spec.md) for the
underlying economic rules this menu implements — this doc covers only the menu/screen itself.

## Scope

- The Trade Hub block's interaction screen: one screen for both buying and selling.
- First version: direct player interaction only — no Depositor block (deferred, see `docs/spec.md` Future
  Ideas).
- Greenfield: project is currently the bare NeoForge MDK template, no blocks/menus/screens exist yet.

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

## Selling Flow

Fast by default — quick-sell is the primary path, since a confirmation step on every stack would defeat the
point of a drop-it-in-and-go shop. A **Sell Dialog**, mirroring the Buy Dialog's preview/confirm, is available as
an opt-in for players who want one.

1. **Quick Sell (default)**: player drops a stack into the **Sell Slot**, or shift-clicks (routes to it like any
   container).
2. `SellSlot.mayPlace` checks eligibility before accepting: stackable, default components only
   (`stack.getComponents()` must equal the item's defaults — see `docs/spec.md` Item identity), and
   `ItemDenyList`/`ItemAllowList`. Rejected items never leave the player's inventory/cursor — no "jamming" here,
   unlike the Depositor block's hopper-feed case.
3. On insert, the slot processes immediately: looks up current stock, computes floor-rounded payout, credits
   balance, increments stock, empties the slot.
4. Empties immediately, so repeated shift-clicks (selling several different stacks in a row) work with just one
   slot.
5. **Sell Dialog (opt-in)**: a per-player, client-side toggle on the screen switches the Sell Slot into
   confirm-before-selling mode. Dropping/shift-clicking an item stages it instead of processing it immediately,
   opens a receipt-style breakdown (unit payout × quantity, same precision rules and client/server math parity
   as the Buy Dialog), and requires **Confirm** before the sale executes; **Cancel** — or closing the screen with
   an item still staged — returns it to the player, same as vanilla container close. Toggling back to Quick Sell
   reverts to instant processing.
6. Displayed balance (suffix-formatted, per `spec.md` Display formatting) updates live after each sell, in
   either mode.

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
