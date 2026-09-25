package com.angryguyy.duels.kit;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.stats.DatabaseManager;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player kit layout overrides.
 *
 * <p>Each player may rearrange the items of a kit into their preferred
 * slots using the in-game editor. The layout is stored in the MySQL
 * table {@code duels_player_kits}, one row per slot, keyed by
 * {@code (uuid, kit_id, slot)}. This makes the storage available across
 * every server of a network without additional synchronization.</p>
 *
 * <p>The manager keeps a small in-memory cache of the layouts of the
 * players currently online. The cache is populated asynchronously when
 * a player joins (see {@code PlayerJoinListener}) and evicted on quit.
 * Every read used during a duel is therefore synchronous and never
 * touches the database on the main thread.</p>
 *
 * <p>Writes go through the {@link DatabaseManager} connection pool on
 * an async thread. If the database is unavailable, the change is
 * queued for retry and the cache is still updated so that the current
 * session sees the new layout immediately.</p>
 *
 * <p>If statistics are disabled in config, or the database is offline
 * at startup, the manager degrades gracefully: reads return the base
 * kit and writes are silently dropped after a warning.</p>
 */
public class PlayerKitManager {

    private final DuelsPlugin plugin;
    private final DatabaseManager database;

    /**
     * Cache of player layouts, keyed by player uuid, then by kit id.
     * The innermost map is the slot-to-item mapping.
     */
    private final Map<UUID, Map<String, Map<Integer, ItemStack>>> cache = new HashMap<>();

    /**
     * Set of players whose layout has already been loaded, so we do not
     * re-query the database on every join.
     */
    private final Map<UUID, Boolean> loaded = new HashMap<>();

    /**
     * Creates a new player kit manager.
     *
     * @param plugin   owning plugin, used for logging and scheduling
     * @param database underlying database manager
     */
    public PlayerKitManager(DuelsPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Loads the personal layouts for a player from the database.
     *
     * <p>The operation is performed asynchronously. Until it completes,
     * reads via {@link #resolveForPlayer(UUID, Kit)} return the base kit
     * layout. This is acceptable because the load happens on join, well
     * before the player can start a duel.</p>
     *
     * @param uuid player uuid
     */
    public void loadForPlayer(UUID uuid) {
        if (!database.isReady()) return;
        if (loaded.containsKey(uuid)) return;

        loaded.put(uuid, Boolean.TRUE);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Map<Integer, ItemStack>> kits = new HashMap<>();
            String sql = "SELECT kit_id, slot, item_data FROM duels_player_kits WHERE uuid = ?";
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String kitId = rs.getString("kit_id");
                        int slot = rs.getInt("slot");
                        byte[] bytes = rs.getBytes("item_data");
                        ItemStack item = ItemStack.deserializeBytes(bytes);
                        kits.computeIfAbsent(kitId, k -> new HashMap<>()).put(slot, item);
                    }
                }
            } catch (SQLException e) {
                Log.error(e, "Failed to load kit layouts for %s", uuid);
            }

            synchronized (cache) {
                cache.put(uuid, kits);
            }
            int total = kits.values().stream().mapToInt(Map::size).sum();
            if (total > 0) {
                Log.debug("Loaded %d kit layout slot(s) for %s", total, uuid);
            }
        });
    }

    /**
     * Removes the cached layouts for a player.
     *
     * <p>Called on quit to keep the cache bounded to the online player
     * base. If the player rejoins, the layout is fetched again from the
     * database.</p>
     *
     * @param uuid player uuid
     */
    public void unloadForPlayer(UUID uuid) {
        synchronized (cache) {
            cache.remove(uuid);
        }
        loaded.remove(uuid);
    }

    /**
     * Checks whether a player has a personal layout for a kit.
     *
     * @param uuid  player uuid
     * @param kitId kit id
     * @return {@code true} if an override is cached, even an empty one
     */
    public boolean hasOverride(UUID uuid, String kitId) {
        synchronized (cache) {
            Map<String, Map<Integer, ItemStack>> kits = cache.get(uuid);
            return kits != null && kits.containsKey(kitId);
        }
    }

    /**
     * Returns the personal layout for a kit, or {@code null} if none.
     *
     * <p>The returned map is a live reference to the cache. Callers
     * must not mutate it; use {@link #setOverride(UUID, String, Map)}
     * to change the stored layout.</p>
     *
     * @param uuid  player uuid
     * @param kitId kit id
     * @return the layout, or {@code null}
     */
    public Map<Integer, ItemStack> getOverride(UUID uuid, String kitId) {
        synchronized (cache) {
            Map<String, Map<Integer, ItemStack>> kits = cache.get(uuid);
            if (kits == null) return null;
            return kits.get(kitId);
        }
    }

    /**
     * Saves a personal layout for a kit.
     *
     * <p>The in-memory cache is updated immediately so the next duel
     * uses the new layout. The database write is performed asynchronously
     * in a single transaction: existing rows for the kit are deleted and
     * the new ones are inserted. If the database is unavailable, the
     * write is queued for retry.</p>
     *
     * @param uuid   player uuid
     * @param kitId  kit id
     * @param layout slot-to-item map
     */
    public void setOverride(UUID uuid, String kitId, Map<Integer, ItemStack> layout) {
        Map<Integer, ItemStack> copy = new HashMap<>();
        layout.forEach((slot, item) -> copy.put(slot, item == null ? null : item.clone()));

        synchronized (cache) {
            cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(kitId, copy);
        }
        loaded.put(uuid, Boolean.TRUE);

        if (!database.isReady()) {
            Log.warn("Cannot persist kit layout for %s: database not ready.", uuid);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection()) {
                persistLayout(conn, uuid, kitId, copy);
            } catch (SQLException e) {
                Log.error(e, "Failed to save kit layout for %s/%s; queueing for retry.",
                        uuid, kitId);
                database.queue(conn -> {
                    try {
                        persistLayout(conn, uuid, kitId, copy);
                    } catch (SQLException ex) {
                        Log.error(ex, "Retry failed for kit layout %s/%s", uuid, kitId);
                    }
                });
            }
        });
    }

    /**
     * Persists a layout inside a single transaction.
     *
     * <p>The player row is upserted first to satisfy the foreign key,
     * then all previous rows for the kit are deleted and the new ones
     * are inserted.</p>
     *
     * @param conn   active connection
     * @param uuid   player uuid
     * @param kitId  kit id
     * @param layout slot-to-item map
     * @throws SQLException if any statement fails
     */
    private void persistLayout(Connection conn, UUID uuid, String kitId,
                               Map<Integer, ItemStack> layout) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO duels_players (uuid, username) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, plugin.getServer().getOfflinePlayer(uuid).getName() != null
                        ? plugin.getServer().getOfflinePlayer(uuid).getName()
                        : uuid.toString().substring(0, 16));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM duels_player_kits WHERE uuid = ? AND kit_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitId);
                ps.executeUpdate();
            }

            if (!layout.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO duels_player_kits (uuid, kit_id, slot, item_data) "
                                + "VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<Integer, ItemStack> entry : layout.entrySet()) {
                        if (entry.getValue() == null) continue;
                        ps.setString(1, uuid.toString());
                        ps.setString(2, kitId);
                        ps.setInt(3, entry.getKey());
                        ps.setBytes(4, entry.getValue().serializeAsBytes());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Removes a personal layout for a kit.
     *
     * <p>The cache is updated immediately and the database delete is
     * performed asynchronously.</p>
     *
     * @param uuid  player uuid
     * @param kitId kit id
     */
    public void clearOverride(UUID uuid, String kitId) {
        synchronized (cache) {
            Map<String, Map<Integer, ItemStack>> kits = cache.get(uuid);
            if (kits != null) {
                kits.remove(kitId);
                if (kits.isEmpty()) cache.remove(uuid);
            }
        }

        if (!database.isReady()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM duels_player_kits WHERE uuid = ? AND kit_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitId);
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to clear kit layout for %s/%s", uuid, kitId);
            }
        });
    }

    /**
     * Returns the effective kit for a player.
     *
     * <p>If the player has a personal layout for the given kit, a copy
     * of the kit with that layout is returned. Otherwise the base kit
     * is returned unchanged. This method never blocks on the database;
     * it only reads the in-memory cache.</p>
     *
     * @param uuid    player uuid
     * @param baseKit the base kit definition
     * @return the kit that should be applied to the player
     */
    public Kit resolveForPlayer(UUID uuid, Kit baseKit) {
        Map<Integer, ItemStack> layout;
        synchronized (cache) {
            Map<String, Map<Integer, ItemStack>> kits = cache.get(uuid);
            if (kits == null) return baseKit;
            layout = kits.get(baseKit.getId());
        }
        if (layout == null) return baseKit;
        return baseKit.withLayout(layout);
    }

    /**
     * Removes every cached layout.
     *
     * <p>Used during shutdown to release references. The database is
     * left untouched.</p>
     */
    public void shutdown() {
        synchronized (cache) {
            cache.clear();
        }
        loaded.clear();
    }
}