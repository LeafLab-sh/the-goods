package sh.leaflab.goods.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import sh.leaflab.goods.TheGoods;

public class EconomyData extends SavedData {
    // Bumped from 1: added lifetime_fees (Milestone 7). Old saves without the field default to 0 via
    // optionalFieldOf — no migration logic needed for an additive change like this.
    public static final int DATA_VERSION = 2;

    // data_version is written on every save (always the current DATA_VERSION) but not otherwise used yet — it
    // exists so a future migration can tell an old save file apart from a current one before this mod has ever
    // needed to change its format.
    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("data_version").forGetter(data -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("balances").forGetter(EconomyData::balances),
            TradeRequest.CODEC.listOf().fieldOf("requests").forGetter(EconomyData::requests),
            Codec.LONG.optionalFieldOf("lifetime_fees", 0L).forGetter(EconomyData::lifetimeFees)
    ).apply(instance, (loadedDataVersion, balances, requests, lifetimeFees) -> new EconomyData(balances, requests, lifetimeFees)));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TheGoods.MODID, "economy"),
            () -> new EconomyData(new HashMap<>(), new ArrayList<>(), 0L),
            CODEC
    );

    private final Map<UUID, Long> balances;
    private final List<TradeRequest> requests;
    private long lifetimeFees;

    private EconomyData(Map<UUID, Long> balances, List<TradeRequest> requests, long lifetimeFees) {
        this.balances = new HashMap<>(balances);
        this.requests = new ArrayList<>(requests);
        this.lifetimeFees = lifetimeFees;
    }

    private Map<UUID, Long> balances() {
        return balances;
    }

    private List<TradeRequest> requests() {
        return requests;
    }

    private long lifetimeFees() {
        return lifetimeFees;
    }

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    public void setBalance(UUID player, long amount) {
        balances.put(player, amount);
        setDirty();
    }

    /** Sum of every player's balance — an inflation signal, since /goods give injects unbacked currency. */
    public long totalCirculation() {
        long total = 0L;
        for (long balance : balances.values()) {
            total = Currency.saturatingAdd(total, balance);
        }
        return total;
    }

    public long getLifetimeFees() {
        return lifetimeFees;
    }

    public void addLifetimeFees(long amount) {
        lifetimeFees = Currency.saturatingAdd(lifetimeFees, amount);
        setDirty();
    }

    public Optional<TradeRequest> findRequest(UUID requester, UUID payer) {
        return requests.stream()
                .filter(r -> r.requester().equals(requester) && r.payer().equals(payer))
                .findFirst();
    }

    /** Adds a request, replacing any existing one from the same requester to the same payer. */
    public void putRequest(TradeRequest request) {
        requests.removeIf(r -> r.requester().equals(request.requester()) && r.payer().equals(request.payer()));
        requests.add(request);
        setDirty();
    }

    /** @return true if a matching request was found and removed */
    public boolean removeRequest(UUID requester, UUID payer) {
        boolean removed = requests.removeIf(r -> r.requester().equals(requester) && r.payer().equals(payer));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Requests where the given player would have to pay if they accept. */
    public List<TradeRequest> incomingRequests(UUID payer) {
        return requests.stream().filter(r -> r.payer().equals(payer)).toList();
    }

    /** Requests the given player has sent asking to receive money. */
    public List<TradeRequest> outgoingRequests(UUID requester) {
        return requests.stream().filter(r -> r.requester().equals(requester)).toList();
    }
}
