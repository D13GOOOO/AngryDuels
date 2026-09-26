package com.angryguyy.duels.spectator;

import com.angryguyy.duels.DuelsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Applies spectator-specific rules.
 *
 * <p>Three concerns are handled:</p>
 * <ul>
 *     <li><b>Radius enforcement</b>: when a spectator moves beyond the
 *     configured radius from the arena's spectator spawn, they are
 *     teleported back to the border of the allowed area.</li>
 *     <li><b>Chat blocking</b>: spectators cannot write in global
 *     chat, so their messages never reach the duelists.</li>
 *     <li><b>Disconnect cleanup</b>: a spectator who quits is removed
 *     from the manager and their snapshot is restored on next join by
 *     the existing join listener.</li>
 * </ul>
 *
 * <p>Chat blocking happens on the async chat thread, where only the
 * event cancellation is performed; the feedback message to the player
 * is scheduled on the main thread, since {@code MessagesManager} is not
 * thread-safe.</p>
 */
public class SpectatorListener implements Listener {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public SpectatorListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Keeps spectators inside the configured radius.
     *
     * <p>When the destination is outside the radius, it is clamped to a
     * point at 95% of the radius along the same direction, so the
     * viewer stays inside the allowed area and keeps their original
     * yaw and pitch.</p>
     *
     * @param event the move event
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        SpectatorEntry entry = plugin.spectators().getEntry(event.getPlayer().getUniqueId());
        if (entry == null) return;

        Location center = entry.spectatorSpawn();
        Location to = event.getTo();
        if (center == null || to == null) return;
        if (!center.getWorld().equals(to.getWorld())) return;

        double radius = plugin.spectators().radius();
        double dx = to.getX() - center.getX();
        double dy = to.getY() - center.getY();
        double dz = to.getZ() - center.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq <= radius * radius) return;

        double distance = Math.sqrt(distanceSq);
        double factor = (radius * 0.95) / distance;
        Location clamped = new Location(
                center.getWorld(),
                center.getX() + dx * factor,
                center.getY() + dy * factor,
                center.getZ() + dz * factor,
                to.getYaw(),
                to.getPitch()
        );
        event.setTo(clamped);
    }

    /**
     * Blocks chat for spectators.
     *
     * <p>The event is cancelled on the async chat thread, but the
     * feedback message is dispatched to the main thread so that
     * {@code MessagesManager} is only touched from the thread it was
     * documented for.</p>
     *
     * @param event the chat event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.spectators().isSpectating(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);

        Player viewer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.messages().send(viewer, "spectator.chat-blocked"));
    }

    /**
     * Removes the spectator entry when a player disconnects.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.spectators().isSpectating(event.getPlayer().getUniqueId())) return;
        plugin.spectators().stopSpectating(event.getPlayer());
    }

    /**
     * Prevents a spectator from being teleported by other plugins
     * outside the allowed area.
     *
     * <p>Teleports with a {@code PLUGIN} cause are whitelisted, since
     * they are the ones performed by the spectator manager itself when
     * the spectator joins or leaves. For every other cause, a teleport
     * to a different world or beyond the radius is cancelled.</p>
     *
     * @param event the teleport event
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        SpectatorEntry entry = plugin.spectators().getEntry(event.getPlayer().getUniqueId());
        if (entry == null) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        Location center = entry.spectatorSpawn();
        Location to = event.getTo();
        if (center == null || to == null) return;
        if (!center.getWorld().equals(to.getWorld())) {
            event.setCancelled(true);
            return;
        }
        double radius = plugin.spectators().radius();
        if (center.distanceSquared(to) > radius * radius) {
            event.setCancelled(true);
        }
    }
}