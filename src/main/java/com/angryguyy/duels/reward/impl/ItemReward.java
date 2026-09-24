package com.angryguyy.duels.reward.impl;

import com.angryguyy.duels.reward.Reward;
import com.angryguyy.duels.reward.RewardContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Reward that gives a predefined item stack to the winner.
 *
 * <p>If the winner's inventory cannot hold the whole stack, the
 * leftover items are dropped at their feet so no reward is lost.</p>
 *
 * <p>The item is cloned both at construction time and at grant time,
 * so the reward can be granted to any number of winners without
 * sharing mutable state with the caller or between grants.</p>
 */
public class ItemReward implements Reward {

    private final ItemStack item;

    /**
     * Creates a new item reward.
     *
     * @param item the item to grant; cloned on construction and on
     *             every grant
     */
    public ItemReward(ItemStack item) {
        this.item = item.clone();
    }

    /**
     * Adds the item to the winner's inventory, dropping leftovers.
     *
     * @param ctx reward context
     */
    @Override
    public void grant(RewardContext ctx) {
        Player winner = ctx.getWinner();
        Map<Integer, ItemStack> leftover = winner.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            winner.getWorld().dropItemNaturally(winner.getLocation(), drop);
        }
    }
}