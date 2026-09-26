package com.angryguyy.duels.event;

import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player has successfully joined a party.
 *
 * <p>Not cancellable: the join has already been persisted by the time
 * listeners receive the event. The event carries the joined party and
 * both players involved, so listeners can implement welcome messages,
 * statistics or integrations without re-querying the party manager.</p>
 */
public class PartyJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party party;
    private final UUID player;
    private final UUID inviter;

    /**
     * Creates a new event.
     *
     * @param party   the party that was joined
     * @param player  uuid of the joining player
     * @param inviter uuid of the player who invited them
     */
    public PartyJoinEvent(Party party, UUID player, UUID inviter) {
        this.party = party;
        this.player = player;
        this.inviter = inviter;
    }

    /**
     * Returns the party that was joined.
     *
     * @return party
     */
    public Party getParty() {
        return party;
    }

    /**
     * Returns the uuid of the joining player.
     *
     * @return player uuid
     */
    public UUID getPlayer() {
        return player;
    }

    /**
     * Returns the uuid of the player who invited the new member.
     *
     * @return inviter uuid
     */
    public UUID getInviter() {
        return inviter;
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