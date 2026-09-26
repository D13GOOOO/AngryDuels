package com.angryguyy.duels.spectator;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.arena.Arena;
import com.angryguyy.duels.duel.DuelSession;
import com.angryguyy.duels.util.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the spectator mode for duels.
 *
 * <p>Any player can spectate an active duel with
 * {@code /duel spectate <player>}, provided the target is currently
 * fighting. The viewer is switched to {@link GameMode#SPECTATOR}, their
 * previous state is captured through the snapshot manager, and a
 * repeating task keeps an action bar with the current health of both
 * teams visible.</p>
 *
 * <p>A spectator is removed automatically when the match they are
 * watching ends, when they disconnect, or when they run
 * {@code /duel unspectate}. On exit, the snapshot is restored, so the
 * viewer returns to their exact position, gamemode and inventory.</p>
 *
 * <p>Only players who are not themselves fighting can spectate. The
 * movement radius, used by {@code SpectatorListener}, is read from
 * {@code spectator.radius-blocks} in the config, defaulting to
 * fifty blocks when absent.</p>
 */
public class SpectatorManager {

    /**
     * Fallback movement radius in blocks.
     */
    private static final int DEFAULT_RADIUS = 50;

    /**
     * Interval between action bar updates, in ticks.
     */
    private static final long ACTION_BAR_INTERVAL_TICKS = 10L;

    /**
     * Delay in ticks before a snapshot is restored after a spectator
     * stops watching.
     */
    private static final long RESTORE_DELAY_TICKS = 2L;

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Active spectators, keyed by viewer uuid.
     */
    private final Map<UUID, SpectatorEntry> spectators = new HashMap<>();

    /**
     * Spectators grouped by the leader of the session they watch.
     */
    private final Map<UUID, Set<UUID>> bySession = new HashMap<>();

    /**
     * The repeating action bar task, or {@code null} when the manager
     * has not been started or has been shut down.
     */
    private BukkitTask actionBarTask;

    /**
     * Creates a new spectator manager.
     *
     * @param plugin owning plugin
     */
    public SpectatorManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the action bar task.
     */
    public void start() {
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::tickActionBar,
                ACTION_BAR_INTERVAL_TICKS, ACTION_BAR_INTERVAL_TICKS);
    }

    /**
     * Stops the action bar task and restores every active spectator to
     * their pre-spectate state.
     */
    public void shutdown() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        for (UUID uuid : new ArrayList<>(spectators.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) stopSpectating(p);
        }
        spectators.clear();
        bySession.clear();
    }

    /**
     * Starts spectating a duel session for the given player.
     *
     * <p>Returns {@code false} if the viewer is already spectating or
     * is themselves fighting, or if the session has no arena or no
     * usable spectator spawn.</p>
     *
     * @param viewer  player who wants to spectate
     * @param session session to watch
     * @return {@code true} if the viewer is now spectating
     */
    public boolean startSpectating(Player viewer, DuelSession session) {
        UUID uuid = viewer.getUniqueId();
        if (spectators.containsKey(uuid)) return false;
        if (plugin.duels().isInDuel(uuid)) return false;

        Arena arena = session.getArena();
        if (arena == null) return false;

        Location spawn = arena.getEffectiveSpectatorSpawn();
        if (spawn == null) return false;

        plugin.snapshots().capture(viewer);
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleport(spawn);

        SpectatorEntry entry = new SpectatorEntry(
                uuid, session.getLeaderA(), arena.getId(), spawn, Instant.now());
        spectators.put(uuid, entry);
        bySession.computeIfAbsent(session.getLeaderA(), k -> new HashSet<>()).add(uuid);

        plugin.messages().send(viewer, "spectator.started");
        Log.debug("Spectator %s started watching session of %s",
                viewer.getName(), session.getLeaderA());
        return true;
    }

    /**
     * Stops spectating for the given player.
     *
     * <p>If the viewer is not spectating, the call is a no-op. The
     * snapshot is restored through the snapshot manager, so the
     * player returns to their exact pre-spectate state. If no snapshot
     * is pending — for example because the manager was restarted — the
     * viewer is simply switched back to survival mode.</p>
     *
     * @param viewer player to stop
     */
    public void stopSpectating(Player viewer) {
        UUID uuid = viewer.getUniqueId();
        SpectatorEntry entry = spectators.remove(uuid);
        if (entry == null) return;

        Set<UUID> set = bySession.get(entry.sessionLeaderUuid());
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) bySession.remove(entry.sessionLeaderUuid());
        }

        viewer.setSpectatorTarget(null);

        if (plugin.snapshots().hasPending(uuid)) {
            plugin.snapshots().scheduleRestore(viewer, RESTORE_DELAY_TICKS);
        } else {
            viewer.setGameMode(GameMode.SURVIVAL);
        }

        plugin.messages().send(viewer, "spectator.stopped");
        Log.debug("Spectator %s stopped watching", viewer.getName());
    }

    /**
     * Removes every spectator of a session.
     *
     * <p>Called by the duel manager when a session ends, so that
     * spectators are automatically restored to their pre-duel state
     * without needing an explicit command. Offline spectators are
     * removed from the tracking structures without any further action,
     * since their snapshot will be restored by the join listener when
     * they reconnect.</p>
     *
     * @param session the session that just ended
     */
    public void cleanup(DuelSession session) {
        Set<UUID> set = bySession.remove(session.getLeaderA());
        if (set == null) return;
        for (UUID uuid : new ArrayList<>(set)) {
            Player p = Bukkit.getPlayer(uuid);
            spectators.remove(uuid);
            if (p != null && p.isOnline()) {
                p.setSpectatorTarget(null);
                if (plugin.snapshots().hasPending(uuid)) {
                    plugin.snapshots().scheduleRestore(p, RESTORE_DELAY_TICKS);
                } else {
                    p.setGameMode(GameMode.SURVIVAL);
                }
                plugin.messages().send(p, "spectator.ended");
            }
        }
    }

    /**
     * Checks whether a player is currently spectating.
     *
     * @param uuid player uuid
     * @return {@code true} if the player is spectating
     */
    public boolean isSpectating(UUID uuid) {
        return spectators.containsKey(uuid);
    }

    /**
     * Returns the spectator entry of a player, if any.
     *
     * @param uuid player uuid
     * @return entry, or {@code null}
     */
    public @Nullable SpectatorEntry getEntry(UUID uuid) {
        return spectators.get(uuid);
    }

    /**
     * Returns the movement radius in blocks.
     *
     * @return radius
     */
    public int radius() {
        return plugin.config().raw().getInt("spectator.radius-blocks", DEFAULT_RADIUS);
    }

    /**
     * Returns the ids of the spectators currently watching a session.
     *
     * @param sessionLeaderUuid leader of the watched session
     * @return set of uuids, possibly empty
     */
    public Set<UUID> getSpectatorsOf(UUID sessionLeaderUuid) {
        return Set.copyOf(bySession.getOrDefault(sessionLeaderUuid, Set.of()));
    }

    /**
     * Returns a snapshot of every active spectator.
     *
     * @return list of entries
     */
    public List<SpectatorEntry> getAll() {
        return new ArrayList<>(spectators.values());
    }

    // ------------------------------------------------------------
    // Action bar
    // ------------------------------------------------------------

    /**
     * Refreshes the action bar of every online spectator.
     *
     * <p>Spectators whose watched session has already ended are
     * skipped silently; their entry is normally removed by
     * {@link #cleanup(DuelSession)} before the next tick.</p>
     */
    private void tickActionBar() {
        if (spectators.isEmpty()) return;

        for (SpectatorEntry entry : new ArrayList<>(spectators.values())) {
            Player viewer = Bukkit.getPlayer(entry.viewer());
            if (viewer == null || !viewer.isOnline()) continue;

            DuelSession session = plugin.duels().getSession(entry.sessionLeaderUuid());
            if (session == null) continue;

            viewer.sendActionBar(buildActionBar(session));
        }
    }

    /**
     * Builds the action bar component for a session.
     *
     * @param session the watched session
     * @return the action bar component
     */
    private Component buildActionBar(DuelSession session) {
        MiniMessage mm = MiniMessage.miniMessage();
        double hpA = totalHealth(session.getTeamA());
        double hpB = totalHealth(session.getTeamB());

        String template;
        if (session.isFfaMode()) {
            template = "<gray>Alive: <white>" + aliveCount(session) + "</white>"
                    + " <dark_gray>|</dark_gray> <gray>Players: <white>"
                    + session.getFfaPlayers().size() + "</white>";
        } else {
            template = "<red>Team A</red> <dark_gray>»</dark_gray> <white>"
                    + String.format(Locale.ROOT, "%.1f", hpA) + " ❤</white>"
                    + "   <dark_gray>|</dark_gray>   "
                    + "<blue>Team B</blue> <dark_gray>»</dark_gray> <white>"
                    + String.format(Locale.ROOT, "%.1f", hpB) + " ❤</white>";
        }
        return mm.deserialize(template);
    }

    /**
     * Sums the health of every online player in a team.
     *
     * @param team list of player uuids
     * @return total health, or {@code 0} if no player is online
     */
    private double totalHealth(List<UUID> team) {
        double total = 0;
        for (UUID uuid : team) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) total += p.getHealth();
        }
        return total;
    }

    /**
     * Counts the alive players of an FFA session.
     *
     * @param session the session
     * @return number of online, non-dead players
     */
    private int aliveCount(DuelSession session) {
        int count = 0;
        for (UUID uuid : session.getFfaPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.isDead()) count++;
        }
        return count;
    }
}