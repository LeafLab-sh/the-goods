# The Goods

**A player-driven economy where prices are set by what's actually in stock — not a fixed price list.**

Every item has a value that rises and falls with how much of it the server has traded in. Flood the market with cobblestone and its price craters; keep a rare item scarce and it stays valuable. There's no admin-authored price list to maintain — the economy prices itself.

## How it works

Sell an item at the **Trade Hub** and it goes into the server's shared stock. The more of an item that's already in stock, the less each additional unit is worth — a logarithmic curve, so payouts taper off smoothly instead of cratering to zero or staying flat forever. Buying an item pulls it back out of stock and pushes the price for the *next* buyer back up. Selling something and immediately buying it back always costs you more than you got paid, so there's no free-money loop to exploit.

## Features

**Trade Hub block**
A single block, craftable from vanilla materials, that's the front door to the whole economy. Right-click it to open the trading interface.

**Sell**
Drag an eligible item stack into the Sell Slot and it's instantly valued and sold — no menus, no waiting. Only plain, unmodified stacks are accepted (no enchanted gear, no renamed items, nothing that could be used to dodge the pricing curve), and server admins can allow-list or deny-list specific items.

**Buy**
A searchable, scrollable catalog of everything currently in stock — sort by name, price, or quantity, ascending or descending. Pick a quantity with `+`/`-`/Max, or hold **Shift** to jump by a full stack and **Ctrl** to jump by 10. The price shown is always exactly what you'll pay; there's no bait-and-switch between the preview and the checkout.

**A real currency**
Every player has a balance, stored precisely (no floating-point rounding weirdness) and persisted with the world. Pay other players directly, or send a payment *request* that shows up as a clickable prompt they can accept, deny, or you can cancel.

**Server-configurable**
- Custom currency name
- Optional transaction fee on purchases
- Item allow-list / deny-list, so admins can keep the economy focused on whatever items make sense for their server

## Commands

- `/goods balance [player]` — check a balance
- `/goods pay <player> <amount>` — send currency directly
- `/goods request <amount> <player>` — ask another player to pay you, with an in-chat Accept/Deny prompt
- `/goods request accept|deny|cancel|list` — manage pending requests
- `/goods give|take|reset <player> [amount]` — admin balance management

## Requirements

- Minecraft 26.2
- NeoForge 26.2.0.7-beta or later

## Coming soon

Server-wide economy metrics (`/goods metrics`), an optional confirm-before-selling mode for the Sell Slot, and further UI polish are planned for an upcoming update.

---

*Found a bug or have a suggestion? [Link to your issue tracker / Discord / GitHub here.]*
