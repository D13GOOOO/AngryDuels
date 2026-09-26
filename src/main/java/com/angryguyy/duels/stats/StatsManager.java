package com.angryguyy.duels.stats;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Provides read and write access to duel statistics.
 *
 * <p>Writes are performed asynchronously through the
 * {@link DatabaseManager} pool. If a write fails because the database
 * is unreachable, the whole transaction is queued for retry and
 * re-executed later by the retry task. Reads are performed
 * synchronously on the calling thread and return {@code null} when the
 * database is not available; callers are expected to invoke them from
 * the main thread only, since they are triggered by player commands and
 * read a single row.</p>
 *
 * <p>The manager never throws SQL exceptions to its callers. Database
 * errors are logged and, where appropriate, queued for retry, so that a
 * temporary outage does not propagate into the duel flow.</p>
 *
 * <p>Instances are not thread-safe; access is expected on the Bukkit
 * main thread. The asynchronous work spawned by this class uses the
 * database pool directly and does not mutate any in-memory state.</p>
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
     * Records a completed duel in the database.
     *
     * <p>The write is performed in a single transaction on an async
     * thread. The readiness check is intentionally performed inside the
     * async task, so that no database-related work ever touches the main
     * thread. If the write fails because the database is unreachable,
     * the whole transaction is re-queued and retried later by the
     * {@link DatabaseManager} retry task.</p>
     *
     * @param winnerUuid winner uuid
     * @param winnerName winner username
     * @param loserUuid  loser uuid, or {@code null}
     * @param loserName  loser username, or {@code null}
     * @param kitId      kit id, or {@code null}
     * @param arenaId    arena id, or {@code null}
     * @param duration   duel duration in seconds
     * @param reason     end reason name
     */
    public void recordDuel(UUID winnerUuid, String winnerName,
                           UUID loserUuid, String loserName,
                           String kitId, String arenaId,
                           long duration, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!database.isReady()) return;
            try (Connection conn = database.getDataSource().getConnection()) {
                doRecord(conn, winnerUuid, winnerName, loserUuid, loserName,
                        kitId, arenaId, duration, reason);
            } catch (SQLException e) {
                Log.error(e, "Failed to record duel stats; queueing for retry.");
                database.queue(conn -> {
                    try {
                        doRecord(conn, winnerUuid, winnerName, loserUuid, loserName,
                                kitId, arenaId, duration, reason);
                    } catch (SQLException ex) {
                        Log.error(ex, "Retry failed for duel stats.");
                    }
                });
            }
        });
    }

    /**
     * Performs the full duel transaction on the given connection.
     *
     * <p>Every statement runs inside a single transaction opened in
     * {@code autocommit=false} mode. The transaction is committed at
     * the end; if any statement throws, the connection is closed
     * without a commit and the {@code autocommit} flag is restored to
     * {@code true} before returning it to the pool.</p>
     *
     * @param conn       active connection
     * @param winnerUuid winner uuid
     * @param winnerName winner username
     * @param loserUuid  loser uuid, or {@code null}
     * @param loserName  loser username, or {@code null}
     * @param kitId      kit id, or {@code null}
     * @param arenaId    arena id, or {@code null}
     * @param duration   duel duration in seconds
     * @param reason     end reason name
     * @throws SQLException if any statement fails
     */
    private void doRecord(Connection conn, UUID winnerUuid, String winnerName,
                          UUID loserUuid, String loserName,
                          String kitId, String arenaId,
                          long duration, String reason) throws SQLException {
        conn.setAutoCommit(false);
        try {
            upsertPlayer(conn, winnerUuid, winnerName);
            if (loserUuid != null) upsertPlayer(conn, loserUuid, loserName);
            updateWinnerStats(conn, winnerUuid, reason);
            if (loserUuid != null) updateLoserStats(conn, loserUuid, reason);
            if (kitId != null) {
                updateKitStats(conn, winnerUuid, kitId, true);
                if (loserUuid != null) updateKitStats(conn, loserUuid, kitId, false);
            }
            insertHistory(conn, winnerUuid, loserUuid, kitId, arenaId, duration, reason);
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Records a team member's win or loss without creating a new history
     * row.
     *
     * <p>Used to attribute stats to the additional members of a winning
     * or losing team beyond the primary player, who is handled by
     * {@link #recordDuel}. The streak update follows the same rule as
     * the primary player: winners increment the streak and update the
     * best, losers reset it to zero.</p>
     *
     * @param uuid  player uuid
     * @param name  player username
     * @param win   {@code true} for a win, {@code false} for a loss
     * @param kitId kit id, or {@code null}
     */
    public void recordTeamMember(UUID uuid, String name, boolean win, String kitId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!database.isReady()) return;
            try (Connection conn = database.getDataSource().getConnection()) {
                doRecordTeamMember(conn, uuid, name, win, kitId);
            } catch (SQLException e) {
                Log.error(e, "Failed to record team member stats for %s; queueing for retry.", uuid);
                database.queue(conn -> {
                    try {
                        doRecordTeamMember(conn, uuid, name, win, kitId);
                    } catch (SQLException ex) {
                        Log.error(ex, "Retry failed for team member stats of %s.", uuid);
                    }
                });
            }
        });
    }

    /**
     * Performs the team member transaction on the given connection.
     *
     * @param conn  active connection
     * @param uuid  player uuid
     * @param name  player username
     * @param win   {@code true} for a win, {@code false} for a loss
     * @param kitId kit id, or {@code null}
     * @throws SQLException if any statement fails
     */
    private void doRecordTeamMember(Connection conn, UUID uuid, String name,
                                    boolean win, String kitId) throws SQLException {
        conn.setAutoCommit(false);
        try {
            upsertPlayer(conn, uuid, name);
            if (win) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duels_stats SET wins = wins + 1, "
                                + "best_streak = GREATEST(best_streak, streak + 1), "
                                + "streak = streak + 1 WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duels_stats SET losses = losses + 1, streak = 0 "
                                + "WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            }
            if (kitId != null) {
                updateKitStats(conn, uuid, kitId, win);
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Ensures the player row and its stats companion row exist.
     *
     * <p>The player row is upserted with the latest username; the
     * stats row is inserted only if missing, so existing counters are
     * never overwritten by this call.</p>
     *
     * @param conn active connection
     * @param uuid player uuid
     * @param name last known username
     * @throws SQLException if the upsert fails
     */
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

    /**
     * Increments winner counters.
     *
     * <p>The order of assignments matters: MySQL evaluates SET clauses
     * left to right and updates each column immediately.
     * {@code best_streak} must be computed before {@code streak} is
     * incremented, otherwise the GREATEST comparison would read the
     * already-updated value and produce an off-by-one result.</p>
     *
     * @param conn   active connection
     * @param uuid   winner uuid
     * @param reason end reason
     * @throws SQLException if the update fails
     */
    private void updateWinnerStats(Connection conn, UUID uuid, String reason) throws SQLException {
        boolean killed = "PLAYER_DIED".equalsIgnoreCase(reason);
        String sql = "UPDATE duels_stats SET "
                + "wins = wins + 1, "
                + (killed ? "kills = kills + 1, " : "")
                + "best_streak = GREATEST(best_streak, streak + 1), "
                + "streak = streak + 1 "
                + "WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * Increments loser counters.
     *
     * <p>The counter incremented depends on the end reason: a death for
     * {@code PLAYER_DIED}, a forfeit for {@code FORFEIT}, and a quit
     * for {@code PLAYER_QUIT}. Losses are always incremented and the
     * streak is always reset to zero.</p>
     *
     * @param conn   active connection
     * @param uuid   loser uuid
     * @param reason end reason
     * @throws SQLException if the update fails
     */
    private void updateLoserStats(Connection conn, UUID uuid, String reason) throws SQLException {
        boolean died = "PLAYER_DIED".equalsIgnoreCase(reason);
        boolean forfeited = "FORFEIT".equalsIgnoreCase(reason);
        boolean quit = "PLAYER_QUIT".equalsIgnoreCase(reason);

        StringBuilder sql = new StringBuilder("UPDATE duels_stats SET losses = losses + 1, streak = 0");
        if (died) sql.append(", deaths = deaths + 1");
        if (forfeited) sql.append(", forfeits = forfeits + 1");
        if (quit) sql.append(", quits = quits + 1");
        sql.append(" WHERE uuid = ?");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * Increments the per-kit win or loss counter for a player.
     *
     * @param conn  active connection
     * @param uuid  player uuid
     * @param kitId kit id
     * @param win   {@code true} for a win, {@code false} for a loss
     * @throws SQLException if the upsert fails
     */
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

    /**
     * Inserts a row in the historical match log.
     *
     * @param conn     active connection
     * @param winner   winner uuid
     * @param loser    loser uuid, or {@code null}
     * @param kit      kit id, or {@code null}
     * @param arena    arena id, or {@code null}
     * @param duration duel duration in seconds
     * @param reason   end reason
     * @throws SQLException if the insert fails
     */
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
     * <p>The query is performed synchronously on the calling thread,
     * which is acceptable because it is only triggered by player
     * commands and reads a single row. Returns {@code null} if the
     * player has no stats or the database is unavailable.</p>
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
     * Resets a player's stats.
     *
     * <p>Deleting the row in {@code duels_players} cascades to
     * {@code duels_stats} and {@code duels_kit_stats} thanks to the
     * foreign key constraints. The {@code duels_player_kits} table has
     * no foreign key (to avoid collation mismatches between servers),
     * so its rows are deleted manually here. The historical log is left
     * untouched on purpose, so admins can still audit past duels.</p>
     *
     * <p>The transaction runs on an async thread. Failures are logged
     * but not queued for retry, since this is an administrative action
     * that can be repeated from the command.</p>
     *
     * @param uuid player uuid
     */
    public void resetStats(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!database.isReady()) return;
            try (Connection conn = database.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_player_kits WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }
                    throw e;
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }
            } catch (SQLException e) {
                Log.error(e, "Failed to reset stats for %s", uuid);
            }
        });
    }

    /**
     * Resets every stats table.
     *
     * <p>The historical table is also truncated, so this method is
     * intended for full wipes only. Foreign key checks are temporarily
     * disabled to allow truncation in any order. The
     * {@code duels_player_kits} table is included even though it has no
     * foreign key, so a full wipe also clears the player layouts.</p>
     *
     * <p>The operation runs on an async thread. Failures are logged but
     * not queued for retry, since this is an administrative action that
     * can be repeated from the command.</p>
     */
    public void resetAll() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!database.isReady()) return;
            try (Connection conn = database.getDataSource().getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
                st.executeUpdate("TRUNCATE TABLE duels_player_kits");
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