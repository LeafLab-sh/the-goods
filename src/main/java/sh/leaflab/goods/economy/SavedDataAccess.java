package sh.leaflab.goods.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

// Shared by Economy and Stock: both are server-wide SavedData facades that must always read from the overworld's
// storage, never from whatever dimension the caller happens to be in (or there'd be one economy/stock ledger per
// dimension instead of one for the whole server), and must flush immediately after every mutation rather than
// waiting for autosave (see docs/spec.md).
final class SavedDataAccess {
    private SavedDataAccess() {
    }

    static <T extends SavedData> T get(MinecraftServer server, SavedDataType<T> type) {
        return dataStorage(server).computeIfAbsent(type);
    }

    static void flush(MinecraftServer server) {
        dataStorage(server).saveAndJoin();
    }

    private static SavedDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}
