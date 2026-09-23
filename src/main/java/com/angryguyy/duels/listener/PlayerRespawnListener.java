package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Applies pending snapshots after a player respawns.
 *
 * <p>When a duel ends because of a death, the snapshot cannot be
 * restored immediately: the vanilla respawn logic would overwrite the
 * inventory and health as soon as the player finishes respawning.
 * Waiting for the respawn event and scheduling the restore a tick
 * later guarantees the snapshot is the last thing applied.</p>
 */
public class PlayerRespawnListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new respawn listener.
     *
     * @param plugin owning plugin
     */
    public PlayerRespawnListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules a snapshot restore for respawning players that have a
     * pending snapshot.
     *
     * @param event the respawn event
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.snapshots().hasPending(event.getPlayer().getUniqueId())) return;
        plugin.snapshots().scheduleRestore(event.getPlayer(), 3L);
    }
}