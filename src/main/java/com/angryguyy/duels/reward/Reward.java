package com.angryguyy.duels.reward;

/**
 * A single unit of reward that can be granted to the winner of a duel.
 *
 * <p>Implementations are stateless after construction and are expected
 * to be safe to invoke from the Bukkit main thread only. Any failure
 * inside a reward is logged by the {@link RewardManager} and does not
 * prevent the remaining rewards from being granted.</p>
 */
public interface Reward {

    /**
     * Grants this reward to the winner described by the given context.
     *
     * @param ctx the reward context carrying players, arena, kit and
     *            duration information
     */
    void grant(RewardContext ctx);
}