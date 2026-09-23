package com.angryguyy.duels.reward.impl;

import com.angryguyy.duels.reward.Reward;
import com.angryguyy.duels.reward.RewardContext;

/**
 * Reward that gives experience to the winner.
 *
 * <p>Two independent values are supported: raw experience points and
 * whole levels. Both can be set in the same reward entry and are
 * applied in that order.</p>
 */
public class XpReward implements Reward {

    private final int points;
    private final int levels;

    /**
     * Creates a new experience reward.
     *
     * @param points raw experience points, or {@code 0} for none
     * @param levels experience levels, or {@code 0} for none
     */
    public XpReward(int points, int levels) {
        this.points = points;
        this.levels = levels;
    }

    /**
     * Gives the configured experience to the winner.
     *
     * @param ctx reward context
     */
    @Override
    public void grant(RewardContext ctx) {
        if (points > 0) ctx.getWinner().giveExp(points);
        if (levels > 0) ctx.getWinner().giveExpLevels(levels);
    }
}