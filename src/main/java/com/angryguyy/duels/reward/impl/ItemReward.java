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
 */
public class ItemReward implements Reward {

    private final ItemStack item;

    /**
     * Creates a new item reward.
     *
     * @param item the item to grant; stored as-is and cloned on grant
     */
    public ItemReward(ItemStack item) {
        this.item = item;
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