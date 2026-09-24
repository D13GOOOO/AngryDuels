package com.angryguyy.duels.stats;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caches leaderboards read from the database and refreshes them on a
 * fixed schedule.
 *
 * <p>The manager keeps one list per category, ordered by rank. The cache
 * is populated asynchronously on startup and refreshed every N minutes
 * according to the {@code stats.leaderboard.refresh-minutes} setting.
 * Readers access the cached list synchronously and never block the main
 * thread on a database query.</p>
 *
 * <p>Each list holds up to 10 pages worth of entries, so players can
 * browse through pages without triggering a database round trip. The
 * size of the cache is derived from the configured entries per page
 * multiplied by the page cap.</p>
 */
public class LeaderboardManager {

    private static final int PAGE_CAP = 10;

    private final DuelsPlugin plugin;
    private final DatabaseManager database;
    private final Map<LeaderboardCategory, List<LeaderboardEntry>> cache =
            new EnumMap<>(LeaderboardCategory.class);

    private BukkitTask refreshTask;

    /**
     * Creates a new leaderboard manager.
     *
     * @param plugin   owning plugin
     * @param database underlying database manager
     */
    public LeaderboardManager(DuelsPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Starts the refresh task.
     *
     * <p>Performs an immediate refresh on the next async tick and then
     * schedules a repeating refresh at the configured interval. The
     * readiness check uses the cheap flag-only variant, since at this
     * point the pool has just been created and the caller is on the
     * main thread.</p>
     */
    public void start() {
        if (!database.isReady()) {
            Log.warn("Leaderboard disabled: database not ready.");
            return;
        }

        int refreshMinutes = Math.max(1, plugin.config().leaderboardRefreshMinutes());
        long intervalTicks = refreshMinutes * 60L * 20L;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshAll, intervalTicks, intervalTicks);

        Log.info("Leaderboard refresh scheduled every %d minute(s).", refreshMinutes);
    }

    /**
     * Stops the refresh task, if running.
     */
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Refreshes every category in the cache.
     *
     * <p>Before each cycle, a real round-trip is performed against the
     * database to check that it is actually reachable. If the ping fails
     * the refresh is skipped, leaving the existing cached values in
     * place; the next scheduled cycle will retry automatically.</p>
     */
    private void refreshAll() {
        if (!database.ping()) return;
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            try {
                List<LeaderboardEntry> entries = fetch(category);
                synchronized (cache) {
                    cache.put(category, entries);
                }
            } catch (Exception e) {
                Log.error(e, "Failed to refresh leaderboard for %s", category.getId());
            }
        }
    }

    /**
     * Fetches a single category from the database.
     *
     * @param category category to fetch
     * @return list of entries, possibly empty
     * @throws SQLException if the query fails
     */
    private List<LeaderboardEntry> fetch(LeaderboardCategory category) throws SQLException {
        int limit = plugin.config().leaderboardEntriesPerPage() * PAGE_CAP;
        String sql = "SELECT p.uuid, p.username, " + selectExpression(category) + " AS value "
                + "FROM duels_players p JOIN duels_stats s ON p.uuid = s.uuid "
                + "ORDER BY " + category.getOrderClause() + " LIMIT ?";
        List<LeaderboardEntry> out = new ArrayList<>();
        try (Connection conn = database.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    out.add(new LeaderboardEntry(
                            rank++,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getLong("value")
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Returns the SQL expression used to compute the value for a
     * category.
     *
     * @param category category to evaluate
     * @return SQL expression
     */
    private String selectExpression(LeaderboardCategory category) {
        return switch (category) {
            case WINS -> "s.wins";
            case LOSSES -> "s.losses";
            case PLAYED -> "(s.wins + s.losses)";
            case STREAK -> "s.streak";
            case BEST_STREAK -> "s.best_streak";
            case KDR -> "CASE WHEN s.deaths = 0 THEN s.kills ELSE s.kills / s.deaths END";
        };
    }

    /**
     * Returns a page from the cached leaderboard.
     *
     * @param category category to read
     * @param page     one-based page number
     * @return list of entries for the requested page, possibly empty
     */
    public List<LeaderboardEntry> getPage(LeaderboardCategory category, int page) {
        List<LeaderboardEntry> all;
        synchronized (cache) {
            all = cache.getOrDefault(category, List.of());
        }
        int perPage = plugin.config().leaderboardEntriesPerPage();
        int from = Math.max(0, (page - 1) * perPage);
        int to = Math.min(all.size(), from + perPage);
        if (from >= all.size()) return List.of();
        return all.subList(from, to);
    }

    /**
     * Returns the total number of pages for a category.
     *
     * @param category category to inspect
     * @return number of pages, at least 1
     */
    public int totalPages(LeaderboardCategory category) {
        List<LeaderboardEntry> all;
        synchronized (cache) {
            all = cache.getOrDefault(category, List.of());
        }
        int perPage = plugin.config().leaderboardEntriesPerPage();
        return Math.max(1, (int) Math.ceil((double) all.size() / perPage));
    }

    /**
     * Returns the cached entries of a category, for GUI usage.
     *
     * @param category category to read
     * @return immutable copy of the cached list
     */
    public List<LeaderboardEntry> getCached(LeaderboardCategory category) {
        synchronized (cache) {
            return List.copyOf(cache.getOrDefault(category, List.of()));
        }
    }
}