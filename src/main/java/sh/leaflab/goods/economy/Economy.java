package sh.leaflab.goods.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

// Every transaction flushes the save immediately rather than waiting for autosave (see docs/spec.md).
public final class Economy {
    private Economy() {
    }

    public static long getBalance(MinecraftServer server, UUID player) {
        return data(server).getBalance(player);
    }

    public static void give(MinecraftServer server, UUID player, long amount) {
        EconomyData data = data(server);
        data.setBalance(player, Currency.saturatingAdd(data.getBalance(player), amount));
        flush(server);
    }

    public static void take(MinecraftServer server, UUID player, long amount) {
        EconomyData data = data(server);
        data.setBalance(player, Math.max(0L, data.getBalance(player) - amount));
        flush(server);
    }

    public static void reset(MinecraftServer server, UUID player) {
        data(server).setBalance(player, 0L);
        flush(server);
    }

    public static long totalCirculation(MinecraftServer server) {
        return data(server).totalCirculation();
    }

    public static long getLifetimeFees(MinecraftServer server) {
        return data(server).getLifetimeFees();
    }

    public static void addLifetimeFees(MinecraftServer server, long amount) {
        data(server).addLifetimeFees(amount);
        flush(server);
    }

    /** Caller must have already checked `from` has sufficient balance — this performs no validation. */
    public static void transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        EconomyData data = data(server);
        data.setBalance(from, Math.max(0L, data.getBalance(from) - amount));
        data.setBalance(to, Currency.saturatingAdd(data.getBalance(to), amount));
        flush(server);
    }

    public static Optional<TradeRequest> findRequest(MinecraftServer server, UUID requester, UUID payer) {
        return data(server).findRequest(requester, payer);
    }

    public static void putRequest(MinecraftServer server, TradeRequest request) {
        data(server).putRequest(request);
        flush(server);
    }

    /** @return true if a matching request was found and removed */
    public static boolean removeRequest(MinecraftServer server, UUID requester, UUID payer) {
        boolean removed = data(server).removeRequest(requester, payer);
        if (removed) {
            flush(server);
        }
        return removed;
    }

    /**
     * Attempts to accept {@code pending} (a request already known to exist), re-validating {@code payer}'s
     * balance at resolution time — it may have dropped since the request was created. The single source of
     * truth for this guard, shared by GoodsCommand#requestAccept and its GameTest coverage, so a regression here
     * can't silently diverge between production and what's tested.
     *
     * @return true if the balance was sufficient and the transfer happened; false if insufficient (the request
     *         is left untouched, still pending)
     */
    public static boolean acceptRequest(MinecraftServer server, TradeRequest pending) {
        if (getBalance(server, pending.payer()) < pending.amount()) {
            return false;
        }
        removeRequest(server, pending.requester(), pending.payer());
        transfer(server, pending.payer(), pending.requester(), pending.amount());
        return true;
    }

    public static List<TradeRequest> incomingRequests(MinecraftServer server, UUID payer) {
        return data(server).incomingRequests(payer);
    }

    public static List<TradeRequest> outgoingRequests(MinecraftServer server, UUID requester) {
        return data(server).outgoingRequests(requester);
    }

    // Always fetched from the overworld, never from whatever dimension the caller happens to be in, so there is
    // exactly one economy for the whole server rather than one per dimension.
    private static EconomyData data(MinecraftServer server) {
        return SavedDataAccess.get(server, EconomyData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
}
