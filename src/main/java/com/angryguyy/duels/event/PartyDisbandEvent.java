package com.angryguyy.duels.event;

import com.angryguyy.duels.event.PartyLeaveEvent.Reason;
import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a party is disbanded.
 *
 * <p>The cause is exposed as a {@link PartyLeaveEvent.Reason} so that
 * listeners can distinguish between an explicit disband and an
 * automatic one caused by the last member leaving.</p>
 *
 * <p>Not cancellable: the party has already been removed by the time
 * listeners receive the event.</p>
 */
public class PartyDisbandEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party party;
    private final Reason reason;

    /**
     * Creates a new event.
     *
     * @param party  the disbanded party
     * @param reason why the party was disbanded
     */
    public PartyDisbandEvent(Party party, Reason reason) {
        this.party = party;
        this.reason = reason;
    }

    /**
     * Returns the disbanded party.
     *
     * @return party
     */
    public Party getParty() {
        return party;
    }

    /**
     * Returns the reason the party was disbanded.
     *
     * @return disband reason
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * Returns the handler list for this event.
     *
     * @return handler list
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Static accessor required by Bukkit's event dispatch system.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}