package sh.leaflab.goods.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.SavedDataStorage;

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

    public static List<TradeRequest> incomingRequests(MinecraftServer server, UUID payer) {
        return data(server).incomingRequests(payer);
    }

    public static List<TradeRequest> outgoingRequests(MinecraftServer server, UUID requester) {
        return data(server).outgoingRequests(requester);
    }

    // Always fetched from the overworld, never from whatever dimension the caller happens to be in, so there is
    // exactly one economy for the whole server rather than one per dimension.
    private static EconomyData data(MinecraftServer server) {
        return dataStorage(server).computeIfAbsent(EconomyData.TYPE);
    }

    private static SavedDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }

    private static void flush(MinecraftServer server) {
        dataStorage(server).saveAndJoin();
    }
}
