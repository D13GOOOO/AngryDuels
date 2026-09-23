package com.angryguyy.duels.reward.impl;

import com.angryguyy.duels.reward.Reward;
import com.angryguyy.duels.reward.RewardContext;
import org.bukkit.Bukkit;

/**
 * Reward that dispatches a command through the console.
 *
 * <p>Placeholders are resolved before the command is dispatched, so
 * administrators can target the winner, loser, arena or kit directly
 * from the config. Commands are executed as the console sender, which
 * grants them full permissions.</p>
 */
public class ConsoleCommandReward implements Reward {

    private final String command;

    /**
     * Creates a new console command reward.
     *
     * @param command command template, without leading slash
     */
    public ConsoleCommandReward(String command) {
        this.command = command;
    }

    /**
     * Dispatches the resolved command as the console sender.
     *
     * @param ctx reward context
     */
    @Override
    public void grant(RewardContext ctx) {
        String resolved = ctx.resolve(command);
        if (resolved == null || resolved.isEmpty()) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
    }
}