package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.DuelEndEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.time.Instant;

/**
 * Records duel statistics when a duel ends.
 *
 * <p>Each winning player is recorded as a winner, each losing player as
 * a loser, and the match is written to the history table once. The kit
 * statistics are incremented once per winning or losing player so that
 * team matches contribute proportional counters to every participant.</p>
 *
 * <p>Only the primary winner and the primary loser are recorded through
 * {@code StatsManager.recordDuel}, which owns the history row. The
 * remaining members of each team, if any, are recorded through
 * {@code StatsManager.recordTeamMember} so that team matches do not
 * generate multiple history rows for the same fight.</p>
 *
 * <p>When the primary loser is not present in the event — for example
 * when the losing player disconnected — a {@code null} uuid is passed
 * to the manager, matching the nullable loser column in the history
 * table.</p>
 */
public class DuelStatsListener implements Listener {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public DuelStatsListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Records the duel in the database.
     *
     * @param event the duel end event
     */
    @EventHandler
    public void onDuelEnd(DuelEndEvent event) {
        if (event.getWinners().isEmpty()) return;

        long duration = Duration.between(
                event.getSession().getStartedAt(), Instant.now()).getSeconds();

        String arenaId = event.getSession().getArena() != null
                ? event.getSession().getArena().getId() : null;

        Player primaryWinner = event.getWinners().get(0);
        Player primaryLoser = event.getLosers().isEmpty() ? null : event.getLosers().get(0);

        plugin.stats().recordDuel(
                primaryWinner.getUniqueId(), primaryWinner.getName(),
                primaryLoser != null ? primaryLoser.getUniqueId() : null,
                primaryLoser != null ? primaryLoser.getName() : null,
                event.getSession().getKitId(),
                arenaId,
                duration,
                event.getReason().name()
        );

        for (int i = 1; i < event.getWinners().size(); i++) {
            Player w = event.getWinners().get(i);
            plugin.stats().recordTeamMember(w.getUniqueId(), w.getName(), true,
                    event.getSession().getKitId());
        }
        for (int i = 1; i < event.getLosers().size(); i++) {
            Player l = event.getLosers().get(i);
            plugin.stats().recordTeamMember(l.getUniqueId(), l.getName(), false,
                    event.getSession().getKitId());
        }
    }
}