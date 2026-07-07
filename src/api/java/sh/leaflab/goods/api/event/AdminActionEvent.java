package sh.leaflab.goods.api.event;

import java.util.UUID;

import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after an admin action
 * (give, take, or reset) modifies a player's balance.
 */
public class AdminActionEvent extends Event {
    private final String action;
    private final UUID actor;
    private final UUID target;
    private final long amount;

    public AdminActionEvent(String action, UUID actor, UUID target, long amount) {
        this.action = action;
        this.actor = actor;
        this.target = target;
        this.amount = amount;
    }

    public String action() {
        return action;
    }

    public UUID actor() {
        return actor;
    }

    public UUID target() {
        return target;
    }

    public long amount() {
        return amount;
    }
}
