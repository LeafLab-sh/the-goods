# Trade Hub Menu — Design

Implementation design for the Trade Hub block's player-facing GUI. See [`docs/spec.md`](spec.md) for the
underlying economic rules this menu implements — this doc covers only the menu/screen itself.

## Scope

- The Trade Hub block's interaction screen: one screen for both buying and selling.
- First version: direct player interaction only — no Depositor block (deferred, see `docs/spec.md` Future
  Ideas).
- Greenfield: project is currently the bare NeoForge MDK template, no blocks/menus/screens exist yet.

## Architecture & Screen Layout

`TradeHubMenu` (`AbstractContainerMenu`) + `TradeHubScreen` (`AbstractContainerScreen`), three pieces sharing one
screen:

```
┌─────────────────────────────────────────┐
│  [search box______]            Balance:  │
│  ┌───────────────────────────┐  1,234.56 │
│  │  catalog icon grid         │           │
│  │  (scrollable)               │  ▲       │
│  │                             │  ▼ scroll │
│  └───────────────────────────┘           │
│                                            │
│              [ Sell Slot ]                │
│                                            │
│  [ player inventory 3x9 ]                 │
│  [ hotbar ]                                │
└─────────────────────────────────────────┘
```

- **Catalog widget** (top): custom-drawn, not `Slot`-backed — search bar, sort control (Name/Price/Stock,
  asc/desc; travels in `CatalogQueryPacket` so pages come back pre-sorted, not just client re-ordered), and
  scrollable icon grid. 0-stock items are excluded (reappear once sold in). Clicking an icon opens a **Buy
  Dialog** overlay with quantity controls, a receipt-style cost breakdown (see Buying Flow), and Confirm.
- **Sell Slot** (middle): one real `Slot` subclass (`SellSlot`) that processes on insert; drag-drop and
  shift-click both work.
- **Player inventory** (bottom): standard vanilla slots.

Rationale: real `Slot`s where real inventory mechanics are needed; the catalog is custom-rendered since global
stock is a counter, not physical items — same approach as AE2's terminal, Refined Storage, and JEI's item list.
Scales to any number of item types instead of being capped by `Slot` count.

## Buying Flow

1. Search box → debounced `CatalogQueryPacket` (search term + sort mode + scroll offset).
2. Server replies with `CatalogResultPacket`: page of `(itemId, currentStock, unitPriceForBuying1)`.
3. Client renders icons, then keeps them live via server-pushed `StockUpdatePacket`s (see Networking) instead of
   polling. Displayed price/stock is a snapshot — Confirm always re-checks live.
4. Clicking an icon opens the **Buy Dialog**: price-per-item, +/-/max-affordable buttons and typed quantity (both
   clamped to the lesser of affordable-with-fee and available stock), and a receipt-style breakdown (client-computed,
   visual only, full precision per `spec.md` Display formatting): **Subtotal** (unit price × quantity) → **Fee**
   (`TransactionFeePercent` of subtotal) → **Total** (subtotal + fee — the amount actually charged). The client
   must compute this with the exact same formula as the server (`log1p`/`StrictMath`, per `spec.md` Value
   Calculation), so the preview never disagrees with the final charge by even the smallest unit.
5. **Confirm** sends `BuyRequestPacket` (itemId + quantity) — no client-computed price is trusted.
6. Server re-checks stock atomically, recomputes cost (subtotal + `TransactionFeePercent` fee, ceiling-rounded
   once per `spec.md` Rounding Strategy), verifies balance/inventory space, then atomically deducts currency,
   decrements stock, gives items. Displayed balance (suffix-formatted, per `spec.md` Display formatting) updates
   live, same as the Selling Flow.
7. If stock/balance/space no longer suffice, the **whole transaction is rejected** — never a partial buy. Dialog
   refreshes and the player re-confirms.

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

**Packets:**
- `CatalogQueryPacket` (C→S): search term + sort mode + scroll offset.
- `CatalogResultPacket` (S→C): page of `(itemId, stock, unitPrice, quoteHash)` — see Packet integrity.
- `BuyRequestPacket` (C→S): itemId + quantity + quoteHash (echoed from the `CatalogResultPacket` the Buy Dialog
  was opened from).
- `StockUpdatePacket` (S→C): coalesced once per server tick — not per-transaction — and pushed to every player
  with a Trade Hub open if any stock changed that tick, regardless of cause. Requires the server to track open
  `TradeHubMenu`s. Per-tick batching keeps this cheap even once the Depositor block (hopper-fed, higher
  transaction rate) ships.
- Balance sync: vanilla `ContainerData` only carries `short`s — balance needs a custom sync packet (or two
  packed ints) sent on change.

**Packet integrity:** `StreamCodec` rejects malformed payloads at decode time, and price is always
server-recomputed from live stock (step 6) — a forged `BuyRequestPacket` can't buy at an invented price
regardless. `quoteHash` (short hash of `itemId + stock + unitPrice + quoteEpoch`, sent in `CatalogResultPacket`
and echoed back) isn't a security boundary — it's a signal for telling ordinary staleness (hash matches a real
quote, stock just moved since — normal reject-and-refresh) apart from a forged/replayed packet (hash matches
nothing the server ever sent that client — reject and log as suspicious). `itemId`/`quantity` are validated
independently either way: registry membership, allow/deny list, and `0 < quantity <= currentStock` — checked
before any cost is computed, so an out-of-range or overflow-prone `quantity` never reaches the pricing math.

**Edge cases:**
- Full inventory on buy → reject before deducting currency.
- Stock/price changed between snapshot and confirm → reject and refresh.
- Sell Slot rejects ineligible item → stays on cursor/in origin slot, no special handling.
- Sort keys (price, stock) can change while a player is scrolling a sorted page, so an item may shift position,
  duplicate, or briefly vanish across a page boundary. Accepted as normal shopping-list churn for this catalog's
  scale — not worth a stable-cursor mechanism.

**Testing:** no unit test runner (per `CLAUDE.md`) — verified via `runGameTestServer`:
- Sell Slot pays out and updates stock correctly.
- Ineligible items rejected from Sell Slot.
- Closing the screen with a Sell Dialog item staged returns it to the player, not lost.
- Insufficient balance/space buy is fully rejected, no side effects.
- Concurrent buy/sell on the same item stays atomic (no duplication).
- `BuyRequestPacket` with a `quoteHash` that was never sent to that client is rejected and logged, not silently
  repriced.
- `BuyRequestPacket` with `quantity <= 0` or `quantity > currentStock` is rejected without computing cost.
- Buy cost at `TransactionFeePercent` of 0% and 100% rounds and charges correctly (boundary values).
- Catalog results honor each sort mode and exclude 0-stock items.
