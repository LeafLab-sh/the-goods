# The Goods

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/B4N622FRUD)

**A player-driven economy where prices are set by what's actually in stock — not a fixed price list.**

Every item has a value that rises and falls with how much of it the server has traded in. Flood the market with cobblestone and its price craters; keep a rare item scarce and it stays valuable. There's no admin-authored price list to maintain — the economy prices itself.

## How it works

Sell an item at the **Trade Hub** and it goes into the server's shared stock. The more of an item that's already in stock, the less each additional unit is worth — a logarithmic curve, so payouts taper off smoothly instead of cratering to zero or staying flat forever. Buying an item pulls it back out of stock and pushes the price for the *next* buyer back up. Selling something and immediately buying it back always costs you more than you got paid, so there's no free-money loop to exploit.

## Requirements

- Minecraft 26.2
- NeoForge 26.2.0.7-beta or later
- Java 25 (bundled with modern NeoForge installers — you generally don't need to install this yourself)

This is a **server-side economy**: balances and stock are stored per-world/per-server, so all players on a given server share the same market. It needs to be installed on the server; players also need it installed client-side to see the Trade Hub screen.

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 26.2 on your client and/or server.
2. Download `thegoods-<version>.jar` and drop it into the `mods/` folder.
3. Launch the game (or start the server) once to generate the default config, then adjust it if needed — see [Configuration](#configuration) below.

## Getting started

1. **Craft a Trade Hub.** It's a hollow ring of 8 sticks in a 3x3 crafting grid (sticks on all edges and corners, empty in the middle) — yields one Trade Hub block.
2. **Place it** and right-click to open the trading screen.
3. **Sell** something: drag an eligible item stack into the Sell Slot on the left. It sells instantly — no confirmation step, no waiting.
4. **Buy** something: search or browse the catalog on the right side of the screen, click an item, pick a quantity, and hit Confirm.

Only plain, unmodified item stacks can be sold — no enchanted gear, no renamed items, nothing with custom NBT/components. That's intentional: it keeps every unit of a given item identical in value, and stops players from using rare modifications to dodge the pricing curve.

### Buy screen controls

- Type in the search box to filter the catalog by name.
- Click the mode-switch buttons on the left edge of the catalog to change sort (name/price/stock) and direction (ascending/descending).
- Drag the scrollbar or use the mouse wheel to scroll through results.
- On the quantity buttons: a normal click steps by 1, **Shift**-click steps by a full stack, **Ctrl**-click steps by 10. The Max button jumps straight to the largest quantity you can afford (never more than what's in stock).

## Commands

| Command | Who | Description |
|---|---|---|
| `/goods balance [player]` | anyone | Check your own balance, or another player's |
| `/goods pay <player> <amount>` | anyone | Send currency directly, no confirmation needed |
| `/goods request <amount> <player>` | anyone | Ask another player to pay you — they get a clickable Accept/Deny prompt |
| `/goods request accept\|deny\|cancel <player>` | anyone | Respond to or cancel a pending request |
| `/goods request list` | anyone | List your pending incoming and outgoing requests |
| `/goods give <player> <amount>` | op | Add to a player's balance, notifies them if online |
| `/goods take <player> <amount>` | op | Subtract from a player's balance (floors at 0), notifies them if online |
| `/goods reset <player>` | op | Zero out a player's balance, notifies them if online |

`give`/`take`/`pay`/`request` all work with offline players by name, not just players currently online.

## Configuration

After the first launch, edit `config/thegoods-common.toml` (or use the in-game **Mod Options** screen, if your mod menu of choice supports it — NeoForge's config screen API is wired up):

| Key | Default | Description |
|---|---|---|
| `currencyName` | `Credits` | The server-wide display name for the currency |
| `transactionFeePercent` | `0` | Percentage fee charged on **buys only** (0–100); sells are never taxed |
| `itemDenyList` | *(empty)* | Item IDs (e.g. `minecraft:diamond`) excluded from trading. Ignored if `itemAllowList` is non-empty. Denylisting an item that already has stock still lets that stock be bought down to 0 — it only blocks *new* deposits from that point on |
| `itemAllowList` | *(empty)* | If non-empty, **only** these item IDs can be traded, and `itemDenyList` is ignored |

An invalid entry in either list (not a real item ID, or not a registered item) will fail config loading with a specific error in the log rather than silently being dropped — check your server log if the config doesn't seem to be taking effect.

## Building from source

```bash
./gradlew build          # build the mod JAR (output: build/libs/)
./gradlew runClient       # launch a game client with the mod loaded, for testing
./gradlew runServer       # launch a dedicated server, for testing
```

Project design docs (block/economy design, value-curve formulas, GUI layout) live under [`docs/`](docs/).

## Credits

This mod was inspired by a guild concept in [A Tale in The Desert](https://www.desert-nomad.com/), an MMORPG which I played over 15 years ago.
The catalog/scrollbar/side-button sprites in the Buy screen are imported from [Refined Storage 2](https://github.com/refinedmods/refinedstorage2) (MIT license) — see [`REFINEDSTORAGE2_LICENSE.txt`](REFINEDSTORAGE2_LICENSE.txt) for the full attribution and license text.

## License

MIT — see [`LICENSE`](LICENSE).
