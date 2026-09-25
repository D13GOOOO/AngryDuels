package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Recovers pending snapshots when a player reconnects and warms up the
 * per-player kit layout cache.
 *
 * <p>The snapshot recovery handles the crash-during-duel case. The kit
 * layout load populates the cache used by {@code PlayerKitManager} so
 * that the first duel after join does not need to wait for a database
 * round trip.</p>
 */
public class PlayerJoinListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new join listener.
     *
     * @param plugin owning plugin
     */
    public PlayerJoinListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules a snapshot restore and a layout cache load.
     *
     * @param event the join event
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.playerKits().loadForPlayer(event.getPlayer().getUniqueId());

        if (!plugin.snapshots().hasPending(event.getPlayer().getUniqueId())) return;
        plugin.snapshots().scheduleRestore(event.getPlayer(), 10L);
    }
}