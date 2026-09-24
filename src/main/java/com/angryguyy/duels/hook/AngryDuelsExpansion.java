package com.angryguyy.duels.hook;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.stats.StatsSnapshot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

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
 *     <li>{@code %angryduels_streak%} — current win streak</li>
 *     <li>{@code %angryduels_best_streak%} — best win streak</li>
 *     <li>{@code %angryduels_winrate%} — win rate as a percentage</li>
 *     <li>{@code %angryduels_kdr%} — kill/death ratio</li>
 *     <li>{@code %angryduels_rank_wins%} — rank in the wins leaderboard</li>
 * </ul>
 *
 * <p>Identifiers are resolved from the database on each request. This
 * makes placeholders always fresh but slower than cached values; if
 * performance becomes a concern, a caching layer can be added later.</p>
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
        UUID uuid = player.getUniqueId();

        StatsSnapshot s = plugin.stats().readStats(uuid);
        if (s == null) return "0";

        return switch (params.toLowerCase()) {
            case "wins" -> String.valueOf(s.wins());
            case "losses" -> String.valueOf(s.losses());
            case "played" -> String.valueOf(s.totalDuels());
            case "kills" -> String.valueOf(s.kills());
            case "deaths" -> String.valueOf(s.deaths());
            case "forfeits" -> String.valueOf(s.forfeits());
            case "quits" -> String.valueOf(s.quits());
            case "streak" -> String.valueOf(s.streak());
            case "best_streak" -> String.valueOf(s.bestStreak());
            case "winrate" -> String.format("%.1f", s.winRate());
            case "kdr" -> String.format("%.2f", s.kdr());
            case "rank_wins" -> String.valueOf(findRankWins(uuid));
            default -> null;
        };
    }

    /**
     * Finds the wins rank of a player from the cached leaderboard.
     *
     * @param uuid player uuid
     * @return rank, or {@code 0} if not ranked
     */
    private int findRankWins(UUID uuid) {
        for (var entry : plugin.leaderboards()
                .getCached(com.angryguyy.duels.stats.LeaderboardCategory.WINS)) {
            if (entry.uuid().equals(uuid)) return entry.rank();
        }
        return 0;
    }
}