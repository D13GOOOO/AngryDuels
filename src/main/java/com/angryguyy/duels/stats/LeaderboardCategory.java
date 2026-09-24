package com.angryguyy.duels.stats;

import java.util.Locale;

/**
 * Enumerates the leaderboard categories available to players and
 * administrators.
 *
 * <p>Each category maps to a specific SQL ordering and a display label
 * used in command output and in the GUI title. The {@link #fromString}
 * helper parses a category from a command argument in a case-insensitive
 * way.</p>
 */
public enum LeaderboardCategory {

    /**
     * Ranking by total wins.
     */
    WINS("wins", "Wins", "wins DESC"),

    /**
     * Ranking by total losses.
     */
    LOSSES("losses", "Losses", "losses DESC"),

    /**
     * Ranking by total duels played.
     */
    PLAYED("played", "Duels Played", "(wins + losses) DESC"),

    /**
     * Ranking by current win streak.
     */
    STREAK("streak", "Current Streak", "streak DESC"),

    /**
     * Ranking by best win streak ever achieved.
     */
    BEST_STREAK("beststreak", "Best Streak", "best_streak DESC"),

    /**
     * Ranking by kill/death ratio.
     */
    KDR("kdr", "K/D Ratio", "CASE WHEN deaths = 0 THEN kills ELSE kills / deaths END DESC");

    private final String id;
    private final String label;
    private final String orderClause;

    LeaderboardCategory(String id, String label, String orderClause) {
        this.id = id;
        this.label = label;
        this.orderClause = orderClause;
    }

    /**
     * Returns the command-friendly id of the category.
     *
     * @return category id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable label.
     *
     * @return display label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the SQL ORDER BY clause associated with this category.
     *
     * @return order clause without the leading ORDER BY keyword
     */
    public String getOrderClause() {
        return orderClause;
    }

    /**
     * Resolves a category from a command argument.
     *
     * @param input argument, possibly {@code null}
     * @return the matching category, or {@code null} if none matches
     */
    public static LeaderboardCategory fromString(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase(Locale.ROOT);
        for (LeaderboardCategory c : values()) {
            if (c.id.equals(lower)) return c;
        }
        return null;
    }
}