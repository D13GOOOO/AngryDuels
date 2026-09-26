package com.angryguyy.duels.event;

import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player has successfully joined a party.
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

    public Party getParty() { return party; }
    public UUID getPlayer() { return player; }
    public UUID getInviter() { return inviter; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}