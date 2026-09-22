package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.duel.DuelEndReason;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Watches player deaths and concludes the corresponding duel.
 *
 * <p>When a player who is part of an active session dies, the opponent
 * is resolved as the winner and the session is ended with
 * {@link DuelEndReason#PLAYER_DIED}. The handler is intentionally
 * minimal: it does not touch drops, respawn logic, or inventory
 * restoration, which are handled elsewhere (or deferred to a later
 * phase of the plugin).</p>
 */
public class PlayerDeathListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new death listener.
     *
     * @param plugin owning plugin, used to look up sessions
     */
    public PlayerDeathListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles a player death.
     *
     * <p>If the deceased player is not part of a session, the handler
     * returns without doing anything, leaving vanilla death behaviour
     * untouched.</p>
     *
     * @param event the death event
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        DuelSession session = plugin.duels().getSession(deceased.getUniqueId());
        if (session == null) return;

        UUID opponentId = session.opponentOf(deceased.getUniqueId());
        Player winner = opponentId != null ? Bukkit.getPlayer(opponentId) : null;

        plugin.duels().endSession(session, winner, DuelEndReason.PLAYER_DIED);
    }
}