package com.angryguyy.duels;

import com.angryguyy.duels.arena.ArenaManager;
import com.angryguyy.duels.command.DuelsCommand;
import com.angryguyy.duels.config.ConfigManager;
import com.angryguyy.duels.config.MessagesManager;
import com.angryguyy.duels.duel.DuelManager;
import com.angryguyy.duels.kit.KitManager;
import com.angryguyy.duels.listener.DuelProtectionListener;
import com.angryguyy.duels.listener.PlayerDeathListener;
import com.angryguyy.duels.listener.PlayerJoinListener;
import com.angryguyy.duels.listener.PlayerQuitListener;
import com.angryguyy.duels.listener.PlayerRespawnListener;
import com.angryguyy.duels.listener.KitGuiListener;
import com.angryguyy.duels.listener.LeaderboardGuiListener;
import com.angryguyy.duels.snapshot.SnapshotManager;
import com.angryguyy.duels.util.Log;
import com.angryguyy.duels.world.DuelWorldManager;
import com.angryguyy.duels.listener.DuelRewardListener;
import com.angryguyy.duels.reward.RewardManager;
import com.angryguyy.duels.stats.DatabaseManager;
import com.angryguyy.duels.stats.StatsManager;
import com.angryguyy.duels.stats.LeaderboardManager;
import com.angryguyy.duels.listener.DuelStatsListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class and service locator for the whole Duels plugin.
 *
 * <p>See the individual manager classes for details about the
 * subsystems they own. This class is only responsible for the plugin
 * lifecycle: creating managers in dependency order during
 * {@code onEnable}, wiring up commands and listeners, and tearing
 * everything down on {@code onDisable}.</p>
 */
public final class DuelsPlugin extends JavaPlugin {

    private static DuelsPlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private DuelWorldManager worldManager;
    private DuelManager duelManager;
    private ArenaManager arenaManager;
    private SnapshotManager snapshotManager;
    private KitManager kitManager;
    private RewardManager rewardManager;
    private DatabaseManager databaseManager;
    private StatsManager statsManager;
    private LeaderboardManager leaderboardManager;

    /**
     * Called by the server when the plugin is enabled.
     *
     * <p>Instantiates all managers in dependency order, loads persisted
     * snapshots and kits, registers commands and listeners, and logs a
     * startup banner.</p>
     */
    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.messagesManager = new MessagesManager(this);
        this.worldManager = new DuelWorldManager(this);
        worldManager.init();
        this.arenaManager = new ArenaManager(this);
        arenaManager.load();
        this.kitManager = new KitManager(this);
        kitManager.load();
        this.rewardManager = new RewardManager(this);
        rewardManager.load();
        this.databaseManager = new DatabaseManager(this);
        this.statsManager = new StatsManager(this, databaseManager);
        databaseManager.init();
        this.leaderboardManager = new LeaderboardManager(this, databaseManager);
        leaderboardManager.start();
        this.snapshotManager = new SnapshotManager(this);
        snapshotManager.loadAll();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.angryguyy.duels.hook.AngryDuelsExpansion(this).register();
            Log.info("PlaceholderAPI expansion registered.");
        }

        this.duelManager = new DuelManager(this);
        duelManager.start();

        registerCommands();
        registerListeners();

        Log.info("Ready. Kits: %d, Arenas: %d, Vault: %s",
                kitManager.all().size(),
                arenaManager.all().size(),
                rewardManager.getEconomy() != null ? "yes" : "no");
    }

    /**
     * Called by the server when the plugin is disabled.
     */
    @Override
    public void onDisable() {
        if (leaderboardManager != null) leaderboardManager.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
        if (duelManager != null) duelManager.shutdown();
        instance = null;
    }

    /**
     * Registers the {@code /duels} command and its tab completer.
     */
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

    /**
     * Registers all event listeners required by the plugin.
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KitGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelRewardListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelStatsListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaderboardGuiListener(this), this);
    }

    public static DuelsPlugin getInstance() { return instance; }
    public ConfigManager config() { return configManager; }
    public MessagesManager messages() { return messagesManager; }
    public DuelWorldManager worlds() { return worldManager; }
    public DuelManager duels() { return duelManager; }
    public ArenaManager arenas() { return arenaManager; }
    public SnapshotManager snapshots() { return snapshotManager; }
    public KitManager kits() { return kitManager; }
    public RewardManager rewards() { return rewardManager; }
    public StatsManager stats() { return statsManager; }
    public DatabaseManager database() { return databaseManager; }
    public LeaderboardManager leaderboards() { return leaderboardManager; }
}