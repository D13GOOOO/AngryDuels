package com.angryguyy.duels.event;

import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player has left a party.
 */
public class PartyLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party party;
    private final UUID player;
    private final Reason reason;

    /**
     * Creates a new event.
     *
     * @param party  the party that was left
     * @param player uuid of the leaving player
     * @param reason why the player left
     */
    public PartyLeaveEvent(Party party, UUID player, Reason reason) {
        this.party = party;
        this.player = player;
        this.reason = reason;
    }

    public Party getParty() { return party; }
    public UUID getPlayer() { return player; }
    public Reason getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

    /**
     * Why a player left a party.
     */
    public enum Reason {

        /** The player used {@code /party leave}. */
        LEFT,

        /** The player used {@code /party leave} and a new leader was promoted. */
        LEFT_PROMOTED,

        /** The player was the last member and the party was disbanded. */
        LEFT_LAST,

        /** The player was removed by the leader. */
        KICKED,

        /** The leader disbanded the party explicitly. */
        DISBANDED
    }
}