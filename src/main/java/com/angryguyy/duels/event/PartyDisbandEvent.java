package com.angryguyy.duels.event;

import com.angryguyy.duels.event.PartyLeaveEvent.Reason;
import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.HandlerList;

/**
 * Fired when a party is disbanded.
 *
 * <p>The cause is exposed as a {@link PartyLeaveEvent.Reason} so that
 * listeners can distinguish between an explicit disband and an
 * automatic one caused by the last member leaving.</p>
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

    public Party getParty() { return party; }
    public Reason getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}