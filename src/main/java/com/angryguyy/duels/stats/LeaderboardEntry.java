package com.angryguyy.duels.stats;

import java.util.UUID;

/**
 * Immutable entry of a leaderboard.
 *
 * @param rank     one-based rank inside the leaderboard
 * @param uuid     player uuid
 * @param username last known username
 * @param value    numeric value relevant to the category
 */
public record LeaderboardEntry(
        int rank,
        UUID uuid,
        String username,
        long value
) {
}