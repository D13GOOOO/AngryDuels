package com.angryguyy.duels.reward.impl;

import com.angryguyy.duels.reward.Reward;
import com.angryguyy.duels.reward.RewardContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Reward that broadcasts a MiniMessage-formatted message to the whole
 * server.
 *
 * <p>Placeholders are resolved before parsing, so a broadcast can
 * include winner name, kit, arena and duration. The message is sent
 * with no prefix; administrators can add one manually if desired.</p>
 */
public class BroadcastReward implements Reward {

    private final String message;

    /**
     * Creates a new broadcast reward.
     *
     * @param message MiniMessage message template
     */
    public BroadcastReward(String message) {
        this.message = message;
    }

    /**
     * Broadcasts the resolved message to all online players.
     *
     * @param ctx reward context
     */
    @Override
    public void grant(RewardContext ctx) {
        String resolved = ctx.resolve(message);
        if (resolved == null || resolved.isEmpty()) return;
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(resolved));
    }
}