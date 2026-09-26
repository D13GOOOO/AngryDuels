package com.angryguyy.duels.event;

import com.angryguyy.duels.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a party has been created.
 *
 * <p>Not cancellable: the party has already been stored by the time
 * listeners receive the event. The event carries both the freshly
 * created {@link Party} and the player who created it.</p>
 */
public class PartyCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party party;
    private final Player leader;

    /**
     * Creates a new event.
     *
     * @param party  the new party
     * @param leader the player who created it
     */
    public PartyCreateEvent(Party party, Player leader) {
        this.party = party;
        this.leader = leader;
    }

    /**
     * Returns the new party.
     *
     * @return party
     */
    public Party getParty() {
        return party;
    }

    /**
     * Returns the creating player.
     *
     * @return leader
     */
    public Player getLeader() {
        return leader;
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