package com.angryguyy.duels.snapshot;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages {@link PlayerSnapshot} capture, persistence and restore.
 *
 * <p>The manager keeps a live map of snapshots for players currently in
 * a duel, and mirrors it on disk in {@code snapshots.yml}. The file is
 * rewritten on every capture and every restore so that a server crash
 * during a duel does not lose the original player state.</p>
 *
 * <p>The on-disk mirror is written synchronously on the main thread.
 * This is acceptable because the file is small, but it means a single
 * capture or restore can block the server for the duration of a disk
 * write. If inventories grow into the hundreds of items in a future
 * iteration, the write should be moved to an async task guarded by a
 * dirty flag.</p>
 *
 * <p>On startup, {@link #loadAll()} reads the file and repopulates the
 * in-memory map without applying any snapshot. Snapshots are then
 * applied by the join listener when the affected player reconnects,
 * which covers the crash-during-duel recovery case.</p>
 *
 * <p>Restores are always scheduled through {@link #scheduleRestore(Player, long)}
 * so that they run after the current event cycle has finished; applying
 * a snapshot synchronously from inside a damage or death event can be
 * overwritten by the server on the same tick.</p>
 */
public class SnapshotManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, PlayerSnapshot> pending = new HashMap<>();
    private final File file;

    /**
     * Creates a new snapshot manager.
     *
     * @param plugin owning plugin, used for data folder access and
     *               scheduling
     */
    public SnapshotManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "snapshots.yml");
    }

    /**
     * Loads persisted snapshots from disk.
     *
     * <p>Any snapshot already present in memory is discarded before the
     * file is read. Entries that fail to deserialize are logged and
     * skipped individually so that a single corrupt entry does not
     * prevent recovery of the others.</p>
     */
    public void loadAll() {
        pending.clear();
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("snapshots");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                Map<String, Object> values = sec.getValues(false);
                PlayerSnapshot snapshot = PlayerSnapshot.deserialize(values);
                pending.put(snapshot.getUuid(), snapshot);
            } catch (Exception e) {
                Log.error(e, "Failed to load snapshot '%s'", key);
            }
        }
        Log.info("Loaded %d pending snapshot(s).", pending.size());
    }

    /**
     * Captures the current state of a player and persists it.
     *
     * <p>If a snapshot for the same player already exists, it is
     * overwritten; the plugin treats a player as having at most one
     * active duel.</p>
     *
     * @param player player to capture
     */
    public void capture(Player player) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        pending.put(player.getUniqueId(), snapshot);
        save();
        Log.debug("Captured snapshot for %s", player.getName());
    }

    /**
     * Checks whether a snapshot exists for the given player.
     *
     * @param uuid player uuid
     * @return {@code true} if a snapshot is waiting to be restored
     */
    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * Restores a player and removes the corresponding snapshot.
     *
     * <p>If no snapshot exists for the player, the call is a no-op.</p>
     *
     * @param player player to restore
     */
    public void restore(Player player) {
        PlayerSnapshot snapshot = pending.remove(player.getUniqueId());
        if (snapshot == null) return;
        snapshot.apply(player);
        save();
        Log.debug("Restored snapshot for %s", player.getName());
    }

    /**
     * Schedules a restore to run after the given delay.
     *
     * <p>The player is re-fetched by uuid inside the scheduled task,
     * so a disconnect-and-reconnect cycle between scheduling and
     * execution is handled correctly and the snapshot is applied to the
     * new player instance.</p>
     *
     * <p>Using a delayed task also ensures the restore is applied after
     * any vanilla logic that runs in the same tick as the triggering
     * event, which is required when a snapshot is restored right after
     * a respawn.</p>
     *
     * @param player     player to restore
     * @param delayTicks delay in ticks before the restore runs
     */
    public void scheduleRestore(Player player, long delayTicks) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current != null && current.isOnline()) {
                restore(current);
            }
        }, delayTicks);
    }

    /**
     * Discards all in-memory and on-disk snapshots.
     *
     * <p>This method is intended for admin recovery only; calling it
     * while duels are active will leave those players without a way to
     * recover their pre-duel state.</p>
     */
    public void clearAll() {
        pending.clear();
        save();
    }

    /**
     * Returns the number of snapshots waiting to be restored.
     *
     * @return pending snapshot count
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Writes the current snapshot map to disk.
     *
     * <p>The file is fully rewritten on each call. Failures are logged
     * and swallowed so that a filesystem error does not propagate to
     * the duel flow.</p>
     */
    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerSnapshot> entry : pending.entrySet()) {
            cfg.set("snapshots." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            Log.error(e, "Failed to save snapshots.yml");
        }
    }
}