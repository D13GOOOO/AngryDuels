package com.angryguyy.duels.hook;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.stats.LeaderboardCategory;
import com.angryguyy.duels.stats.StatsSnapshot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion that exposes duel statistics and leaderboard
 * positions to other plugins.
 *
 * <p>Available identifiers:</p>
 * <ul>
 *     <li>{@code %angryduels_wins%} — total wins</li>
 *     <li>{@code %angryduels_losses%} — total losses</li>
 *     <li>{@code %angryduels_played%} — total duels played</li>
 *     <li>{@code %angryduels_kills%} — total kills</li>
 *     <li>{@code %angryduels_deaths%} — total deaths</li>
 *     <li>{@code %angryduels_forfeits%} — total forfeits</li>
 *     <li>{@code %angryduels_quits%} — total quits</li>
 *     <li>{@code %angryduels_streak%} — current win streak</li>
 *     <li>{@code %angryduels_best_streak%} — best win streak</li>
 *     <li>{@code %angryduels_winrate%} — win rate as a percentage</li>
 *     <li>{@code %angryduels_kdr%} — kill/death ratio</li>
 *     <li>{@code %angryduels_rank_wins%} — rank in the wins leaderboard</li>
 * </ul>
 *
 * <p>Identifiers are resolved from the database on each request, so the
 * values are always fresh. If the database is unavailable, an empty
 * string is returned instead of a misleading {@code 0}, so that
 * scoreboards and holograms do not flash incorrect numbers during a
 * database outage.</p>
 */
public class AngryDuelsExpansion extends PlaceholderExpansion {

    private final DuelsPlugin plugin;

    /**
     * Creates the expansion.
     *
     * @param plugin owning plugin
     */
    public AngryDuelsExpansion(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "angryduels";
    }

    @Override
    public @NotNull String getAuthor() {
        return "angryguyy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "";

        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("rank_wins")) {
            return String.valueOf(findRank(player.getUniqueId(), LeaderboardCategory.WINS));
        }

        if (!plugin.database().isReady()) {
            return "";
        }

        StatsSnapshot s = plugin.stats().readStats(player.getUniqueId());
        if (s == null) return "";

        return switch (key) {
            case "wins" -> String.valueOf(s.wins());
            case "losses" -> String.valueOf(s.losses());
            case "played" -> String.valueOf(s.totalDuels());
            case "kills" -> String.valueOf(s.kills());
            case "deaths" -> String.valueOf(s.deaths());
            case "forfeits" -> String.valueOf(s.forfeits());
            case "quits" -> String.valueOf(s.quits());
            case "streak" -> String.valueOf(s.streak());
            case "best_streak" -> String.valueOf(s.bestStreak());
            case "winrate" -> String.format(Locale.ROOT, "%.1f", s.winRate());
            case "kdr" -> String.format(Locale.ROOT, "%.2f", s.kdr());
            default -> null;
        };
    }

    /**
     * Finds the rank of a player inside a cached leaderboard category.
     *
     * <p>The lookup is performed on the cached list, so it does not
     * trigger any database query. The rank is one-based, and {@code 0}
     * is returned for players not present in the cache.</p>
     *
     * @param uuid     player uuid
     * @param category category to search
     * @return rank, or {@code 0} if not ranked
     */
    private int findRank(UUID uuid, LeaderboardCategory category) {
        for (var entry : plugin.leaderboards().getCached(category)) {
            if (entry.uuid().equals(uuid)) return entry.rank();
        }
        return 0;
    }
}