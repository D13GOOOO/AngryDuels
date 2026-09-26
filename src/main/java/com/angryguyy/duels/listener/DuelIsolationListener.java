package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.duel.DuelPhase;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/**
 * Restricts what a duelist can do while a match is running.
 *
 * <p>Two distinct levels of isolation are enforced:</p>
 * <ul>
 *     <li><b>Countdown phase</b> — teleporting, dropping items and
 *     interacting with the world are blocked, so the player stays on
 *     their spawn with their kit intact until the fight begins.</li>
 *     <li><b>Active phase</b> — teleport commands and world modification
 *     are blocked, preventing the use of {@code /tp}, {@code /spawn} or
 *     similar commands to escape combat, and preventing block breaking
 *     or placing inside the duel world.</li>
 * </ul>
 *
 * <p>The listener only affects players who are part of an active
 * session. Players who happen to be inside the duel world without
 * being in a match are not restricted by this handler.</p>
 */
public class DuelIsolationListener implements Listener {

    /**
     * Command names blocked while a duelist is in an active session.
     *
     * <p>The list covers common teleport, kit-reload, and ender-chest
     * commands that would otherwise allow a player to escape the arena
     * or alter their kit mid-fight.</p>
     */
    private static final List<String> BLOCKED_COMMANDS = List.of(
            "tp", "teleport", "spawn", "home", "warp", "back",
            "tpa", "tpaccept", "tpyes", "tpdeny", "tpno",
            "kit", "kits", "ec", "enderchest", "pv", "playervault"
    );

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new isolation listener.
     *
     * @param plugin owning plugin
     */
    public DuelIsolationListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Blocks teleportation during the countdown phase.
     *
     * <p>This covers portals, plugin teleports, and vanilla teleports.
     * Teleportation is allowed during the active phase, since it may be
     * needed by the death-and-respawn flow.</p>
     *
     * @param event the teleport event
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        DuelSession session = plugin.duels().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.getPhase() == DuelPhase.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks item drops during the countdown phase.
     *
     * <p>Prevents players from dumping their kit on the ground before
     * the fight starts.</p>
     *
     * @param event the drop event
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        DuelSession session = plugin.duels().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.getPhase() == DuelPhase.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks interaction with the world during the countdown phase.
     *
     * <p>Prevents chests, crafting tables, buttons and similar
     * interactions while the player is frozen on their spawn.</p>
     *
     * @param event the interact event
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        DuelSession session = plugin.duels().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.getPhase() == DuelPhase.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks block breaking inside the duel world for duelists.
     *
     * <p>The duel world is a controlled environment; allowing terrain
     * modification would let a player reshape the arena mid-fight.</p>
     *
     * @param event the block break event
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.duels().isInDuel(player.getUniqueId())) return;
        if (plugin.worlds().isDuelWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks block placing inside the duel world for duelists.
     *
     * @param event the block place event
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.duels().isInDuel(player.getUniqueId())) return;
        if (plugin.worlds().isDuelWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks escape commands for players taking part in a duel.
     *
     * <p>The check applies to both the countdown and the active phase,
     * since a player should not be able to escape via teleport commands
     * once a session has been created. The command name is compared
     * case-insensitively against a fixed blocklist, after stripping any
     * namespace prefix such as {@code minecraft:} or {@code essentials:}.</p>
     *
     * <p>This is a defensive measure against accidental use of teleport
     * or kit reload commands during a fight; it is not a security
     * boundary, since commands with custom aliases can be registered by
     * other plugins.</p>
     *
     * @param event the command event
     */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        DuelSession session = plugin.duels().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;

        String message = event.getMessage();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String withoutSlash = message.substring(1);
        int space = withoutSlash.indexOf(' ');
        String command = (space == -1 ? withoutSlash : withoutSlash.substring(0, space))
                .toLowerCase(Locale.ROOT);

        int colon = command.indexOf(':');
        if (colon != -1) {
            command = command.substring(colon + 1);
        }

        if (BLOCKED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "duel.command-blocked");
        }
    }
}