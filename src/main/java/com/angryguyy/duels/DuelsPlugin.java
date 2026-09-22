package com.angryguyy.duels;

import com.angryguyy.duels.command.DuelsCommand;
import com.angryguyy.duels.config.ConfigManager;
import com.angryguyy.duels.config.MessagesManager;
import com.angryguyy.duels.util.Log;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuelsPlugin extends JavaPlugin {

    private static DuelsPlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;

    @Override
    public void onEnable() {
        instance = this;

        Log.info("RealisticDuels v%s enabling...", getPluginMeta().getVersion());

        this.configManager = new ConfigManager(this);
        this.messagesManager = new MessagesManager(this);

        registerCommands();

        // TODO Phase 2: DuelManager, listeners
        // TODO Phase 3: DuelWorldManager
        // TODO Phase 4: Snapshot manager
        // TODO Phase 5: KitManager
        // TODO Phase 6: Reward manager
        // TODO Phase 7: DatabaseManager
        // TODO Phase 8: Leaderboard

        Log.info("RealisticDuels enabled.");
    }

    @Override
    public void onDisable() {
        Log.info("RealisticDuels disabled.");
        instance = null;
    }

    private void registerCommands() {
        DuelsCommand cmd = new DuelsCommand(this);
        PluginCommand pluginCommand = getCommand("duels");
        if (pluginCommand == null) {
            Log.error("Command 'duels' is not defined in plugin.yml!");
            return;
        }
        pluginCommand.setExecutor(cmd);
        pluginCommand.setTabCompleter(cmd);
    }

    public static DuelsPlugin getInstance() {
        return instance;
    }

    public ConfigManager config() {
        return configManager;
    }

    public MessagesManager messages() {
        return messagesManager;
    }
}