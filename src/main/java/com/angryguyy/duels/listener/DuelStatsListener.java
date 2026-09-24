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
 * <p>The listener reads all necessary data from the session and the two
 * players, then delegates to the {@code StatsManager} which performs the
 * database writes asynchronously.</p>
 */
public class DuelStatsListener implements Listener {

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
        Player winner = event.getWinner();
        if (winner == null) return;
        Player loser = event.getLoser();

        long duration = Duration.between(
                event.getSession().getStartedAt(), Instant.now()).getSeconds();

        String arenaId = event.getSession().getArena() != null
                ? event.getSession().getArena().getId() : null;

        plugin.stats().recordDuel(
                winner.getUniqueId(), winner.getName(),
                loser != null ? loser.getUniqueId() : null,
                loser != null ? loser.getName() : null,
                event.getSession().getKitId(),
                arenaId,
                duration,
                event.getReason().name()
        );
    }
}