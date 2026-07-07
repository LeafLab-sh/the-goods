# The Goods — Design Spec

## Concept

The world's stock is global and server-wide: every Trade Hub is a window into the same shared inventory.
Narratively, players are trading with an off-world spaceship (or another dimension) that holds the actual stock.

## Blocks

- **Trade Hub** block placed in world. Interacting with any side opens the trade interface. First version: direct
  player interaction only, no automated/hopper-fed deposit (see Future Ideas).
- **Obtainable via crafting**, appears in a creative mode tab. **Temporary recipe** (placeholder, not balanced): a
  hollow 3×3 box of sticks (8 border slots filled, center empty) yields 1 Trade Hub.

## Slash Commands

### Available to All

- `/goods balance` — shows current currency balance.
- `/goods pay [Amount] [PlayerName]` — transfers currency immediately, no confirmation needed. Rejected outright
  (no partial transfer) if the sender's balance is insufficient.
- `/goods request [Amount] [PlayerName]` — requests payment from the target; moves nothing until accepted. Target
  gets a chat message with clickable **Accept**/**Deny** buttons (vanilla `click_event` → `run_command`) plus the
  commands below. One pending request per (sender, target) pair — a new request replaces the old one.
- Both: `Amount` must be positive, full precision (10 decimal places); `PlayerName` can never be the sender.
  `PlayerName` resolves via the server's UUID cache (profile lookup), not just currently-online players — you can
  `pay` or `request` an offline player and the update applies immediately to their persisted balance. A
  `PlayerName` with no cached profile (never seen by this server) is rejected with an error, same as any other
  invalid target.
- `/goods request accept` / `deny [PlayerName]` — resolve a pending request from that player (same as the chat
  buttons). Accept re-validates the sender's balance; if it's no longer sufficient, the request just fails,
  nothing moves.
- `/goods request cancel [PlayerName]` — sender-side retraction of their own pending request to that player. No-op
  if nothing is pending.
- `/goods request list` — lists both your pending incoming requests (awaiting your Accept/Deny) and your
  outgoing requests (awaiting the target's response); outgoing entries are cancellable via the existing
  `/goods request cancel [PlayerName]`.
- Pending requests never expire — they persist (across restarts, since they're saved) until accepted, denied, or
  cancelled. No TTL is currently planned.

### Admins Only

- `/goods metrics` — server-wide economy health (see Metrics). Requires op-level 2.
- `/goods give` / `take [Amount] [PlayerName]` — admin currency injection/removal, not stock-backed. **Debugging
  only.** Requires op-level 4. `take` floors at a balance of 0 — it can never drive a balance negative.
- `/goods reset [PlayerName]` — wipes a balance. Requires op-level 4.

## Settings

- **CurrencyName** — server-wide currency name.
- **TransactionFeePercent** — tax on purchases only (selling is never taxed). Bound to **0–100%** inclusive.
  Collected fees are tracked in `/goods metrics` then discarded — a pure currency sink, no redirect.
- **ItemDenyList** — default mode; excludes specific items (e.g. elytra, totems, netherite gear) from an
  otherwise-open economy. Empty by default. Denylisting (or de-allowlisting) an item that already has stock still
  lets buyers drain it to 0, but blocks new deposits immediately.
- **ItemAllowList** — if populated, becomes an exclusive whitelist and `ItemDenyList` is ignored. Empty by
  default.

## Menus

Interacting with the Trade Hub opens the trade interface: player inventory bottom, store inventory top, balance
top right. Withdrawing decreases currency; depositing increases it.

Stock is global and shared, so every open Trade Hub reflects the same stock — trades update live across every
open interface.

## Value Calculation

- Value of having _n_ items in stock: `log2(n+1)`
- Selling _k_ more into stock of _j_: receive `log2(j+k+1)-log2(j+1)`
- Buying _k_ of _j_ in stock: pay `log2(j+1)-log2(j-k+1)`
- **Computation (mandatory)**: implement these as a single `log1p` call, not as a difference of two `log2`
  calls — `log2(j+k+1)-log2(j+1) = log1p(k/(j+1.0)) / ln(2)` for selling, and
  `log2(j+1)-log2(j-k+1) = -log1p(-k/(j+1.0)) / ln(2)` for buying. Subtracting two nearly-equal logs at large
  `j` is catastrophic cancellation — it destroys precision in exactly the low-order digits the Rounding
  Strategy section below depends on. Use `StrictMath`, not `Math`, for `log1p`/`log`: `Math` methods aren't
  guaranteed bit-identical across JVMs/platforms, while `StrictMath` is — required since this value is
  persisted and mints/destroys real currency. The client's preview math (see `docs/trade-hub-menu-design.md`
  Buying Flow) must use this exact same formula, so the previewed total never disagrees with the
  server-charged total.
- Buying is hard-capped at current stock (no negative stock) — a new item type can't be bought until someone
  sells it first. Supply is player-driven.
- Currency is created on sell and destroyed on buy (paid by/to the off-world trading partner) — not a shared
  player pool. Fee revenue (pure sink) and `/goods give`/`take` (unbacked debug) are the only currency outside
  this loop.

**Stock storage**: 64-bit counters per item (32-bit could overflow on a long-running automated farm). A credit
that would exceed the max simply saturates at the max — same overflow policy as balances (see Overflow below)
— rather than wrapping or erroring.

**Concurrency**: trades against shared stock are atomic — concurrent trades on the same item can't both succeed
against stock only one should have gotten.

**Persistence**: stock counts and player balances persist via NeoForge `SavedData`, server-wide (not
per-dimension), surviving restarts. A new player's balance starts at `0`. Every transaction (buy, sell, pay,
accepted request, admin give/take/reset) immediately calls `setDirty()` and forces a save (flush) on the relevant
`SavedData` rather than waiting for the next autosave — a crash right after a trade can't leave only one side of
it persisted.

Each `SavedData` payload carries an explicit integer **data version** tag, incremented whenever the on-disk
schema changes, so a future format migration can detect and upgrade older saves instead of silently misreading
them.

**Item identity**: stock is keyed by exact `ItemStack` (item + components), not just item ID — but only items in
their **default component state** are tradeable at all. Any deviation (custom name, lore, enchantments, firework
data, banner patterns, etc.) is rejected, same hard-rule tier as non-stackable items (see Limitations). This
closes an exploit where cheap, high-cardinality component variation (e.g. free anvil renames) would let a player
mint endless `n=0` stock buckets and bypass the diminishing-returns curve.

### Rounding Strategy

Currency is quantized to **10 decimal places**, stored as fixed-point (not float) to avoid drift — cent
precision would zero out the marginal value of an item once stock reaches the tens of millions
(`log2(j+2)-log2(j+1) ≈ 1/((j+1)·ln 2)`, as small as ~1.4×10⁻⁸).

**Overflow**: balances are a 64-bit fixed-point `long` (~±922M units of headroom). A credit that would exceed the
max simply saturates at the max rather than wrapping or erroring.

The fee-adjusted trade value is computed once and rounded once — never twice. Rounding direction is fixed:
**payouts round down**, **charges round up**. A sale can pay out $0.00; a purchase can never cost $0.00
(ceiling-rounding a positive value always rounds up to at least one unit). This makes a buy-then-sell round trip
never profitable — break-even at best, at exact integer log boundaries where there's nothing to round — closing
the wash-trading exploit.

**Display formatting**: `/goods balance` and a trade about to be confirmed always show full 10-decimal
precision. Elsewhere (tooltips, GUI totals, `/goods metrics`), values use a K/M/G/T/P/E/Z/Y suffix scheme (plain
below 1,000; otherwise divided to the nearest power of 1,000, ≤2 decimals, no trailing zeros — e.g. `12.5M`,
`3K`), per the scaling scheme in `formatDouble` from
[InfiniteStorageCell's `InfinityCellItem`](https://github.com/nutant233/InfiniteStorageCell/blob/1.21/src/main/java/org/infinitestoragecell/InfinityCellItem.java).
Abbreviation always **floors**, so a display never overstates spendable value.

## Limitations

- **Stackable items only** — `maxStackSize > 1` (excludes shulker boxes, written books, etc.; ender pearls, eggs,
  snowballs still qualify). Hard rule; `ItemAllowList` cannot override it, since a non-stackable item can never
  accumulate meaningful stock.
- **Default components only** — see Item identity above. Same hard rule, same `ItemAllowList` exemption.

## Metrics

`/goods metrics` should report, to start:

- Number of distinct item types in stock
- Total value of all stock (sum of `log2(n+1)`)
- Total transaction fees collected (lifetime)
- Total currency in circulation (sum of all balances) — inflation signal, since `/goods give` injects unbacked
  currency
- Top N most/least valuable items in stock

## Future Ideas

These are explicitly out of scope for the initial implementation but worth revisiting later:

- **Per-hub stock and currencies**: instead of one global stock, let individual Trade Hubs maintain independent stock, optionally with their own currency or a configured exchange rate against the server currency. Would open the door to a banking/exchange-rate system between hubs.
- **Depositor block** (automated deposits): a block that attaches to the Trade Hub and enables automatic transfer of items into the system via hopper/item pipe, crediting currency to the placer's account as items are transferred. Design notes for when it's picked up:
  - Items rejected by `ItemAllowList`/`ItemDenyList` (or that aren't stackable) are **not** consumed — a rejected item fills whatever input slot the inserter tried to place it in, and that slot stays blocked while other slots keep accepting deposits normally. Only once every slot is blocked does the Depositor stop accepting deposits, matching how a vanilla hopper refuses to push into a full inventory.
  - When fully blocked, the Depositor should output a redstone signal the same way a vanilla hopper/container reads as "full" to a comparator.
  - Crediting currently goes entirely to whoever placed the block, which is unrefined for shared/communal infrastructure. Consider integrating with claim-based mods (or a similar in-mod ownership concept) so credit/access can be shared or restricted based on claim ownership.
- **Configurable value-curve tuning**: the `log2` base is currently fixed. A configurable curve base/scale would let admins tune how quickly the economy "matures" on high-throughput servers.
- **Admin-seeded initial stock**: a command/config to seed starting stock for select items, for servers that don't want a cold-start economy.
- **Tag-based allow/deny lists**: letting admins deny e.g. `#minecraft:tools` instead of listing every item individually.
- **Audit trail for `/goods give`/`/goods take`**: a log of who got given/taken what and by which admin, for accountability.
- **Per-player metrics command**: an admin command (e.g. `/goods metrics [PlayerName]`) to inspect a specific
  player's balance/transaction history, rather than only server-wide aggregates.
- ~~**Interoperability API**~~ **Implemented** — `src/api/java` source set (`ITheGoodsAPI`,
  events on `NeoForge.EVENT_BUS`). See [`docs/interop-api.md`](interop-api.md).
