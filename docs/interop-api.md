# Interoperability API

The Goods exposes a public API that other mods can compile against to read and (with
permission) manipulate the economy.

## Dependency

The API is published as a separate JAR with classifier `api`:

```groovy
// build.gradle (Gradle)
repositories {
    maven {
        url "https://maven.leaflab.sh/releases" // placeholder — URL will change
    }
}
dependencies {
    compileOnly "sh.leaflab.goods:thegoods-neoforge-${minecraft_version}:${thegoods_version}:api"
}
```

## Obtaining the API

```java
ITheGoodsAPI api = ITheGoodsAPI.get();
```

Must not be called before The Goods mod has initialized (i.e. during `@Mod` constructor or
earlier). Safe to call from any server thread after that point.

## What you can do

### Read balances

```java
long balance = api.getBalance(server, playerUUID);
long total    = api.getTotalCirculation(server);
```

### Mutate balances (admin-level — no permission enforcement by the API)

```java
api.giveCurrency(server, playerUUID, amount);   // returns new balance
api.takeCurrency(server, playerUUID, amount);   // returns new balance
api.resetBalance(server, playerUUID);
```

### Transfer between players

```java
boolean ok = api.transferCurrency(server, fromUUID, toUUID, amount);
```

Returns `false` if the sender's balance was insufficient; nothing is moved on failure.

### Read stock

```java
long    stock     = api.getStock(server, item);
Map<Item, Long> all = api.getAllStock(server);  // items with stock > 0 only
```

### Mutate stock

```java
api.creditStock(server, item, quantity);   // returns new stock
api.debitStock(server, item, quantity);    // returns new stock (floors at 0)
```

### Price calculations

```java
long sellPayout = api.calculateSellValue(stockBefore, quantity);   // floor-rounded
long buyCost    = api.calculateBuyCost(stockBefore, quantity);     // ceil-rounded, no fee
long buyWithFee = api.calculateBuyCostWithFee(stockBefore, quantity, feePercent);
```

### Item eligibility

```java
boolean ok = api.isEligible(ItemStack stack);
```

Checks stackability, default components, and allow/deny lists. Does NOT need a server
reference — purely item-property based.

### Payment requests

```java
Optional<TradeRequest> pending = api.findPendingRequest(server, requesterUUID, payerUUID);
```

### Config queries

```java
int    feePercent   = api.getTransactionFeePercent();
String currencyName = api.getCurrencyName();
```

### Permission queries (API never enforces — up to the caller)

```java
api.hasOpLevel(player, 2);               // op level 2+ ?
api.hasGamemasterPermission(player);     // shorthand for level 2
api.hasAdminPermission(player);          // shorthand for level 4
```

## Events

All events are posted on `NeoForge.EVENT_BUS`:

| Event | When | Read-only fields |
|---|---|---|
| `ItemSoldEvent` | A player sells items to the Trade Hub | `player`, `stack`, `quantity`, `payout`, `stockAfter` |
| `ItemBoughtEvent` | A player buys items from the Trade Hub | `player`, `item`, `quantity`, `cost`, `feePercent`, `feeAmount`, `stockAfter` |
| `CurrencyTransferredEvent` | Currency moves between two players (pay, request accept) | `action` ("PAY" or "REQUEST_ACCEPT"), `from`, `to`, `amount` |
| `AdminActionEvent` | An admin runs give/take/reset | `action` ("GIVE"/"TAKE"/"RESET"), `actor`, `target`, `amount` |

Events are fired **after** the state change is persisted, so subscribers see the final
state.

### Example listener

```java
@SubscribeEvent
public static void onItemSold(ItemSoldEvent event) {
    LOGGER.info("{} sold {}x {} for {}", event.player().getScoreboardName(),
            event.quantity(), event.stack().getHoverName().getString(),
            event.payout());
}
```

## Events: fields

### `ItemSoldEvent`
- `player` — `ServerPlayer` who sold
- `stack` — the `ItemStack` sold (exact item + quantity at sale time)
- `quantity` — how many units
- `payout` — fixed-point currency received (floor-rounded)
- `stockAfter` — `Stock.getStock()` immediately after the trade

### `ItemBoughtEvent`
- `player` — `ServerPlayer` who bought
- `item` — the `Item` bought
- `quantity` — how many units
- `cost` — total cost paid including fee (ceil-rounded)
- `feePercent` — the server's configured fee at trade time (0–100)
- `feeAmount` — actual fee collected in fixed-point units (0 if no-fee)
- `stockAfter` — `Stock.getStock()` immediately after the trade

### `CurrencyTransferredEvent`
- `action` — `"PAY"` for a direct `/goods pay`, or `"REQUEST_ACCEPT"` for an accepted request
- `from` — `UUID` of the sender
- `to` — `UUID` of the receiver
- `amount` — fixed-point amount transferred

### `AdminActionEvent`
- `action` — `"GIVE"`, `"TAKE"`, or `"RESET"`
- `actor` — `UUID` of the admin who performed the action (nil UUID for non-player sources, e.g. console/API)
- `target` — `UUID` of the affected player
- `amount` — amount given/taken (0 for RESET)
