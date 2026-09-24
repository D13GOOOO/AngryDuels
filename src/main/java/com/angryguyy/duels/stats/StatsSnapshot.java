package com.angryguyy.duels.stats;

/**
 * Immutable snapshot of a player's aggregated duel statistics.
 *
 * @param username    last known username
 * @param wins        total wins
 * @param losses      total losses
 * @param kills       total kills
 * @param deaths      total deaths
 * @param forfeits    total forfeits
 * @param quits       total quits
 * @param streak      current win streak
 * @param bestStreak  best win streak ever
 */
public record StatsSnapshot(
        String username,
        int wins, int losses,
        int kills, int deaths,
        int forfeits, int quits,
        int streak, int bestStreak
) {
    /**
     * Returns total duels played.
     *
     * @return wins + losses
     */
    public int totalDuels() {
        return wins + losses;
    }

    /**
     * Returns win rate as a percentage.
     *
     * @return win rate between 0.0 and 100.0
     */
    public double winRate() {
        int total = totalDuels();
        return total == 0 ? 0.0 : (wins * 100.0) / total;
    }

    /**
     * Returns the kill/death ratio.
     *
     * @return K/D, or the number of kills if deaths is zero
     */
    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}