package com.angryguyy.duels.event;

import com.angryguyy.duels.party.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player has left a party.
 *
 * <p>Not cancellable: the leave has already been persisted by the time
 * listeners receive the event. The {@link Reason} enum distinguishes
 * between a voluntary leave, a kick, a promotion-triggering leave, and
 * the automatic disband caused by the last member leaving.</p>
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

    /**
     * Returns the party that was left.
     *
     * @return party
     */
    public Party getParty() {
        return party;
    }

    /**
     * Returns the uuid of the leaving player.
     *
     * @return player uuid
     */
    public UUID getPlayer() {
        return player;
    }

    /**
     * Returns the reason the player left.
     *
     * @return leave reason
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

    /**
     * Why a player left a party.
     */
    public enum Reason {

        /**
         * The player used {@code /party leave}.
         */
        LEFT,

        /**
         * The player used {@code /party leave} and a new leader was
         * automatically promoted among the remaining members.
         */
        LEFT_PROMOTED,

        /**
         * The player was the last member and the party was
         * automatically disbanded as a consequence.
         */
        LEFT_LAST,

        /**
         * The player was removed by the leader using the kick command.
         */
        KICKED,

        /**
         * The leader disbanded the party explicitly.
         */
        DISBANDED
    }
}