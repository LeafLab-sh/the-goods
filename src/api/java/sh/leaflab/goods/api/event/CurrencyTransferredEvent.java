package sh.leaflab.goods.api.event;

import java.util.UUID;

import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after currency is transferred
 * between two players (via {@code /goods pay} or a request accept).
 */
public class CurrencyTransferredEvent extends Event {
    private final String action;
    private final UUID from;
    private final UUID to;
    private final long amount;

    public CurrencyTransferredEvent(String action, UUID from, UUID to, long amount) {
        this.action = action;
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public String action() {
        return action;
    }

    public UUID from() {
        return from;
    }

    public UUID to() {
        return to;
    }

    public long amount() {
        return amount;
    }
}
