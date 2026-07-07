package sh.leaflab.goods.economy;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import sh.leaflab.goods.Config;

public final class Audit {
    private Audit() {
    }

    public static void log(MinecraftServer server, String action, UUID actorId, String actorName,
                           UUID targetId, String targetName, long amount, long targetBalance,
                           String itemId, long itemQuantity) {
        if (!Config.AUDIT_LOG_ENABLED.get()) {
            return;
        }
        data(server).addEntry(new AuditEntry(
                server.overworld().getGameTime(), action, actorId, actorName,
                targetId, targetName, amount, targetBalance, itemId, itemQuantity));
        flush(server);
    }

    public static List<AuditEntry> latest(MinecraftServer server, int count) {
        return data(server).latestEntries(count);
    }

    public static int size(MinecraftServer server) {
        return data(server).size();
    }

    private static AuditData data(MinecraftServer server) {
        return SavedDataAccess.get(server, AuditData.TYPE);
    }

    private static void flush(MinecraftServer server) {
        SavedDataAccess.flush(server);
    }
}
