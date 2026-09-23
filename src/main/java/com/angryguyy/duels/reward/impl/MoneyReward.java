package com.angryguyy.duels.reward.impl;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.reward.Reward;
import com.angryguyy.duels.reward.RewardContext;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

/**
 * Reward that deposits money into the winner's balance via Vault.
 *
 * <p>The economy provider is resolved at grant time rather than at
 * construction time, because other plugins may register their economy
 * service after this plugin has been enabled. If no provider is
 * available at grant time, the reward becomes a no-op.</p>
 */
public class MoneyReward implements Reward {

    private final DuelsPlugin plugin;
    private final double amount;

    /**
     * Creates a new money reward.
     *
     * @param plugin owning plugin, used to resolve the economy provider
     * @param amount amount to deposit
     */
    public MoneyReward(DuelsPlugin plugin, double amount) {
        this.plugin = plugin;
        this.amount = amount;
    }

    /**
     * Deposits the configured amount into the winner's balance.
     *
     * @param ctx reward context
     */
    @Override
    public void grant(RewardContext ctx) {
        Economy economy = plugin.rewards().getEconomy();
        if (economy == null) return;
        economy.depositPlayer((OfflinePlayer) ctx.getWinner(), amount);
    }
}