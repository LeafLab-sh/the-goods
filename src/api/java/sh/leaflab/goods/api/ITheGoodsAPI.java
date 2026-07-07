package sh.leaflab.goods.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The primary interop surface for The Goods economy.
 * <p>
 * Obtain via {@link #get()} after The Goods mod has initialized. All methods are safe to call from
 * the server thread. Mutating methods persist immediately (flush SavedData).
 * <p>
 * This interface carries a backward-compatibility commitment: existing methods will not be removed
 * or have their signatures changed in a breaking way within a major version line. Additions are
 * always additive.
 * </p>
 */
public interface ITheGoodsAPI {

    // ========== BALANCE OPERATIONS ==========

    /** Returns {@code player}'s balance in fixed-point units (10 decimal places). */
    long getBalance(MinecraftServer server, UUID player);

    /**
     * Credits {@code amount} to {@code player}'s balance (saturating add).
     *
     * @return the new balance
     */
    long giveCurrency(MinecraftServer server, UUID player, long amount);

    /**
     * Debits {@code amount} from {@code player}'s balance (floors at 0).
     *
     * @return the new balance
     */
    long takeCurrency(MinecraftServer server, UUID player, long amount);

    /** Sets {@code player}'s balance to 0. */
    void resetBalance(MinecraftServer server, UUID player);

    /**
     * Transfers {@code amount} from one player to another.
     *
     * @return {@code true} if the sender had sufficient balance and the transfer succeeded
     */
    boolean transferCurrency(MinecraftServer server, UUID from, UUID to, long amount);

    /** Sum of all player balances (inflation signal). */
    long getTotalCirculation(MinecraftServer server);

    /** Total transaction fees collected (lifetime). */
    long getLifetimeFees(MinecraftServer server);

    // ========== STOCK OPERATIONS ==========

    /** Current stock count for {@code item}. */
    long getStock(MinecraftServer server, Item item);

    /**
     * Adds {@code quantity} to stock (saturating add).
     *
     * @return the new stock count
     */
    long creditStock(MinecraftServer server, Item item, long quantity);

    /**
     * Removes {@code quantity} from stock (floors at 0). Caller must have already checked stock is sufficient.
     *
     * @return the new stock count
     */
    long debitStock(MinecraftServer server, Item item, long quantity);

    /** All items with stock > 0 (defensive copy). */
    Map<Item, Long> getAllStock(MinecraftServer server);

    // ========== TRADE / ELIGIBILITY ==========

    /** Whether {@code stack} can be traded (stackable, default components, passes allow/deny lists). */
    boolean isEligible(ItemStack stack);

    /** Sell payout in fixed-point units for selling {@code quantity} into stock of {@code stockBefore} (floor-rounded). */
    long calculateSellValue(long stockBefore, long quantity);

    /** Buy cost in fixed-point units for buying {@code quantity} from stock of {@code stockBefore} (ceil-rounded, no fee). */
    long calculateBuyCost(long stockBefore, long quantity);

    /** Buy cost including fee, in fixed-point units. {@code feePercent} is 0-100. */
    long calculateBuyCostWithFee(long stockBefore, long quantity, int feePercent);

    // ========== PAYMENT REQUESTS ==========

    /** Finds a pending payment request, if one exists. */
    Optional<TradeRequest> findPendingRequest(MinecraftServer server, UUID requester, UUID payer);

    // ========== QUERIES ==========

    /** Current transaction fee percentage (0-100). */
    int getTransactionFeePercent();

    /** The server-wide currency display name. */
    String getCurrencyName();

    // ========== PERMISSIONS (query only — API never enforces) ==========

    /** Whether {@code player} has at least the given op level. */
    boolean hasOpLevel(ServerPlayer player, int level);

    /** Whether {@code player} has op level 2 (gamemaster — metrics, audit). */
    boolean hasGamemasterPermission(ServerPlayer player);

    /** Whether {@code player} has op level 4 (owner — give, take, reset). */
    boolean hasAdminPermission(ServerPlayer player);

    // ========== SINGLETON ACCESSOR ==========

    /** Returns the API singleton. Must not be called before The Goods mod initializes. */
    static ITheGoodsAPI get() {
        ITheGoodsAPI api = Holder.instance;
        if (api == null) {
            throw new IllegalStateException("ITheGoodsAPI not yet initialized. "
                    + "Ensure The Goods mod has loaded before calling get().");
        }
        return api;
    }

    /**
     * Sets the API singleton. Called internally by The Goods mod during construction.
     * External callers should use {@link #get()} instead.
     */
    static void set(ITheGoodsAPI api) {
        Holder.instance = api;
    }

    class Holder {
        static volatile ITheGoodsAPI instance;
    }
}
