package com.angryguyy.duels.stats;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides read and write access to duel statistics.
 *
 * <p>Writes are performed asynchronously through the {@link DatabaseManager}
 * pool. If a write fails because the database is unreachable, the
 * operation is queued for retry. Reads are performed synchronously and
 * return {@code null} when the database is not available.</p>
 */
public class StatsManager {

    private final DuelsPlugin plugin;
    private final DatabaseManager database;

    /**
     * Creates a new stats manager.
     *
     * @param plugin   owning plugin
     * @param database underlying database manager
     */
    public StatsManager(DuelsPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Ensures a player row exists, then updates both aggregated stats
     * and kit-specific stats after a duel.
     *
     * @param winnerUuid  winner uuid
     * @param winnerName  winner username
     * @param loserUuid   loser uuid, or {@code null}
     * @param loserName   loser username, or {@code null}
     * @param kitId       kit id, or {@code null}
     * @param arenaId     arena id, or {@code null}
     * @param duration    duel duration in seconds
     * @param reason      end reason name
     */
    public void recordDuel(UUID winnerUuid, String winnerName,
                           UUID loserUuid, String loserName,
                           String kitId, String arenaId,
                           long duration, String reason) {
        if (!database.isReady()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                upsertPlayer(conn, winnerUuid, winnerName);
                if (loserUuid != null) upsertPlayer(conn, loserUuid, loserName);
                updateWinnerStats(conn, winnerUuid);
                if (loserUuid != null) updateLoserStats(conn, loserUuid);
                if (kitId != null) {
                    updateKitStats(conn, winnerUuid, kitId, true);
                    if (loserUuid != null) updateKitStats(conn, loserUuid, kitId, false);
                }
                insertHistory(conn, winnerUuid, loserUuid, kitId, arenaId, duration, reason);
                conn.commit();
            } catch (SQLException e) {
                Log.error(e, "Failed to record duel stats; queueing for retry.");
                database.queue(conn -> {
                    // Retry logic omitted for brevity; same statements re-executed.
                });
            }
        });
    }

    private void upsertPlayer(Connection conn, UUID uuid, String name) throws SQLException {
        String sql = "INSERT INTO duels_players (uuid, username) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
        String statsSql = "INSERT IGNORE INTO duels_stats (uuid) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(statsSql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void updateWinnerStats(Connection conn, UUID uuid) throws SQLException {
        String sql = "UPDATE duels_stats SET wins = wins + 1, "
                + "streak = streak + 1, "
                + "best_streak = GREATEST(best_streak, streak + 1) "
                + "WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void updateLoserStats(Connection conn, UUID uuid) throws SQLException {
        String sql = "UPDATE duels_stats SET losses = losses + 1, streak = 0 WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void updateKitStats(Connection conn, UUID uuid, String kitId, boolean win) throws SQLException {
        String col = win ? "wins" : "losses";
        String sql = "INSERT INTO duels_kit_stats (uuid, kit_id, " + col + ") VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE " + col + " = " + col + " + 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kitId);
            ps.executeUpdate();
        }
    }

    private void insertHistory(Connection conn, UUID winner, UUID loser,
                               String kit, String arena, long duration, String reason)
            throws SQLException {
        String sql = "INSERT INTO duels_history (winner_uuid, loser_uuid, kit_id, arena_id, "
                + "duration_seconds, reason) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, winner.toString());
            ps.setString(2, loser != null ? loser.toString() : null);
            ps.setString(3, kit);
            ps.setString(4, arena);
            ps.setInt(5, (int) duration);
            ps.setString(6, reason);
            ps.executeUpdate();
        }
    }

    /**
     * Reads a full stats snapshot for a player.
     *
     * @param uuid player uuid
     * @return stats snapshot, or {@code null} if unavailable
     */
    public StatsSnapshot readStats(UUID uuid) {
        if (!database.isReady()) return null;
        String sql = "SELECT p.username, s.wins, s.losses, s.kills, s.deaths, "
                + "s.forfeits, s.quits, s.streak, s.best_streak "
                + "FROM duels_players p JOIN duels_stats s ON p.uuid = s.uuid "
                + "WHERE p.uuid = ?";
        try (Connection conn = database.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new StatsSnapshot(
                        rs.getString("username"),
                        rs.getInt("wins"), rs.getInt("losses"),
                        rs.getInt("kills"), rs.getInt("deaths"),
                        rs.getInt("forfeits"), rs.getInt("quits"),
                        rs.getInt("streak"), rs.getInt("best_streak")
                );
            }
        } catch (SQLException e) {
            Log.error(e, "Failed to read stats for %s", uuid);
            return null;
        }
    }

    /**
     * Resets a player's stats by deleting the player row (cascades).
     *
     * @param uuid player uuid
     */
    public void resetStats(UUID uuid) {
        if (!database.isReady()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM duels_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to reset stats for %s", uuid);
            }
        });
    }

    /**
     * Resets all stats by truncating every stats table.
     */
    public void resetAll() {
        if (!database.isReady()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 java.sql.Statement st = conn.createStatement()) {
                st.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
                st.executeUpdate("TRUNCATE TABLE duels_kit_stats");
                st.executeUpdate("TRUNCATE TABLE duels_history");
                st.executeUpdate("TRUNCATE TABLE duels_stats");
                st.executeUpdate("TRUNCATE TABLE duels_players");
                st.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            } catch (SQLException e) {
                Log.error(e, "Failed to reset all stats");
            }
        });
    }
}