package com.angryguyy.duels.event;

import com.angryguyy.duels.duel.DuelEndReason;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fired after a duel session has been terminated.
 *
 * <p>This event is fired by the {@code DuelManager} for every possible
 * end scenario, including kills, forfeits, disconnects, cancellations
 * and server shutdown. It is <b>not</b> cancellable: the duel has
 * already concluded by the time listeners receive it.</p>
 *
 * <p>Winners and losers are exposed as immutable lists so that team
 * matches are fully represented. In a 1v1 each list contains a single
 * player, and both lists are empty for a cancelled or drawn duel.</p>
 *
 * <p>For convenience, {@link #getWinner()} and {@link #getLoser()}
 * return the first element of the corresponding list, or {@code null}
 * if the list is empty. New code should prefer the list-based
 * accessors to be team-aware.</p>
 */
public class DuelEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DuelSession session;
    private final List<Player> winners;
    private final List<Player> losers;
    private final DuelEndReason reason;

    /**
     * Creates a new duel end event.
     *
     * @param session the session that just ended
     * @param winners list of winning players, possibly empty
     * @param losers  list of losing players, possibly empty
     * @param reason  the reason the duel ended
     */
    public DuelEndEvent(DuelSession session, List<Player> winners,
                        List<Player> losers, DuelEndReason reason) {
        this.session = session;
        this.winners = List.copyOf(winners);
        this.losers = List.copyOf(losers);
        this.reason = reason;
    }

    /**
     * Returns the session that just ended.
     *
     * @return the ended session
     */
    public DuelSession getSession() {
        return session;
    }

    /**
     * Returns an immutable list of winning players.
     *
     * @return winners, possibly empty
     */
    public List<Player> getWinners() {
        return winners;
    }

    /**
     * Returns an immutable list of losing players.
     *
     * @return losers, possibly empty
     */
    public List<Player> getLosers() {
        return losers;
    }

    /**
     * Returns the first winner, for convenience.
     *
     * @return first winner, or {@code null} if there are none
     */
    public @Nullable Player getWinner() {
        return winners.isEmpty() ? null : winners.get(0);
    }

    /**
     * Returns the first loser, for convenience.
     *
     * @return first loser, or {@code null} if there are none
     */
    public @Nullable Player getLoser() {
        return losers.isEmpty() ? null : losers.get(0);
    }

    /**
     * Returns the reason the duel ended.
     *
     * @return end reason
     */
    public DuelEndReason getReason() {
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