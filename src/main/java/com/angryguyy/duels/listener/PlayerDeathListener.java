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
 * <p>When a duelist dies, the opponent is declared winner and the
 * session ends with {@link DuelEndReason#PLAYER_DIED}. All dropped
 * items and experience are cleared from the death event, because the
 * duelist's original state is restored from a snapshot after the
 * respawn; leaving drops in the world would duplicate items.</p>
 *
 * <p>Restoring the snapshot is delegated to
 * {@link PlayerRespawnListener}, which waits for the respawn to
 * complete before applying it.</p>
 */
public class PlayerDeathListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new death listener.
     *
     * @param plugin owning plugin
     */
    public PlayerDeathListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles a player death.
     *
     * <p>If the deceased is not part of an active session, vanilla
     * behaviour is left untouched.</p>
     *
     * @param event the death event
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        DuelSession session = plugin.duels().getSession(deceased.getUniqueId());
        if (session == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        UUID opponentId = session.opponentOf(deceased.getUniqueId());
        Player winner = opponentId != null ? Bukkit.getPlayer(opponentId) : null;

        plugin.duels().endSession(session, winner, DuelEndReason.PLAYER_DIED);
    }
}