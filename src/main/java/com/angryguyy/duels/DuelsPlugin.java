package com.angryguyy.duels;

import com.angryguyy.duels.arena.ArenaManager;
import com.angryguyy.duels.command.DuelsCommand;
import com.angryguyy.duels.command.PartyCommand;
import com.angryguyy.duels.config.ConfigManager;
import com.angryguyy.duels.config.MessagesManager;
import com.angryguyy.duels.duel.DuelManager;
import com.angryguyy.duels.kit.KitManager;
import com.angryguyy.duels.kit.PlayerKitManager;
import com.angryguyy.duels.listener.DuelIsolationListener;
import com.angryguyy.duels.listener.DuelProtectionListener;
import com.angryguyy.duels.listener.DuelRewardListener;
import com.angryguyy.duels.listener.DuelStatsListener;
import com.angryguyy.duels.listener.KitEditorListener;
import com.angryguyy.duels.listener.KitGuiListener;
import com.angryguyy.duels.listener.LeaderboardGuiListener;
import com.angryguyy.duels.listener.PartyGuiListener;
import com.angryguyy.duels.listener.PartyItemListener;
import com.angryguyy.duels.listener.PlayerDeathListener;
import com.angryguyy.duels.listener.PlayerJoinListener;
import com.angryguyy.duels.listener.PlayerQuitListener;
import com.angryguyy.duels.listener.PlayerRespawnListener;
import com.angryguyy.duels.party.PartyManager;
import com.angryguyy.duels.party.item.PartyItemManager;
import com.angryguyy.duels.party.match.PartyMatchSetupManager;
import com.angryguyy.duels.reward.RewardManager;
import com.angryguyy.duels.snapshot.PlayerSnapshot;
import com.angryguyy.duels.snapshot.SnapshotManager;
import com.angryguyy.duels.stats.DatabaseManager;
import com.angryguyy.duels.stats.LeaderboardManager;
import com.angryguyy.duels.stats.StatsManager;
import com.angryguyy.duels.util.Log;
import com.angryguyy.duels.world.DuelWorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
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
    private PlayerKitManager playerKitManager;
    private RewardManager rewardManager;
    private DatabaseManager databaseManager;
    private StatsManager statsManager;
    private LeaderboardManager leaderboardManager;
    private PartyManager partyManager;
    private PartyItemManager partyItemManager;
    private PartyMatchSetupManager partyMatchSetupManager;

    /**
     * Called by the server when the plugin is enabled.
     *
     * <p>Instantiates all managers in dependency order, loads persisted
     * snapshots and kits, registers commands and listeners, and logs a
     * startup banner. Managers that depend on the database pool are
     * initialized only after {@link DatabaseManager#init()} has run, so
     * they can safely capture a reference to the pool even when the
     * database is currently offline.</p>
     *
     * <p>The {@link PlayerSnapshot} class is registered with the
     * configuration serialization system before any snapshot is loaded,
     * so that pending snapshots on disk can be deserialized correctly
     * at startup.</p>
     */
    @Override
    public void onEnable() {
        instance = this;
        Log.info("AngryDuels v%s loading subsystems...", getPluginMeta().getVersion());

        ConfigurationSerialization.registerClass(PlayerSnapshot.class);

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
        this.playerKitManager = new PlayerKitManager(this, databaseManager);
        this.partyManager = new PartyManager(this, databaseManager);
        partyManager.load();
        this.partyItemManager = new PartyItemManager(partyManager);
        this.partyMatchSetupManager = new PartyMatchSetupManager();

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

        Log.info("Ready. Kits: %d, Arenas: %d, Vault: %s, MySQL: %s",
                kitManager.all().size(),
                arenaManager.all().size(),
                rewardManager.getEconomy() != null ? "yes" : "no",
                databaseManager.isReady() ? "yes" : "no");
    }

    /**
     * Called by the server when the plugin is disabled.
     *
     * <p>Shuts down the managers that hold external resources in
     * reverse initialization order: the leaderboard refresh task, the
     * party and player kit caches, the database pool, and any active
     * duel session. The order matters because the database pool must be
     * closed only after every consumer has stopped using it.</p>
     */
    @Override
    public void onDisable() {
        if (leaderboardManager != null) leaderboardManager.shutdown();
        if (partyMatchSetupManager != null) partyMatchSetupManager.shutdown();
        if (partyItemManager != null) partyItemManager.shutdown();
        if (partyManager != null) partyManager.shutdown();
        if (playerKitManager != null) playerKitManager.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
        if (duelManager != null) duelManager.shutdown();
        instance = null;
    }

    /**
     * Registers the plugin commands and their tab completers.
     */
    private void registerCommands() {
        DuelsCommand cmd = new DuelsCommand(this);
        PluginCommand pluginCommand = getCommand("duels");
        if (pluginCommand == null) {
            Log.error("Command 'duels' is not defined in plugin.yml!");
        } else {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        }

        PartyCommand partyCmd = new PartyCommand(this);
        PluginCommand partyCommand = getCommand("party");
        if (partyCommand == null) {
            Log.error("Command 'party' is not defined in plugin.yml!");
        } else {
            partyCommand.setExecutor(partyCmd);
            partyCommand.setTabCompleter(partyCmd);
        }
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
        getServer().getPluginManager().registerEvents(new DuelIsolationListener(this), this);
        getServer().getPluginManager().registerEvents(new KitGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new KitEditorListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaderboardGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelRewardListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelStatsListener(this), this);
        getServer().getPluginManager().registerEvents(new PartyItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PartyGuiListener(this), this);
    }

    public static DuelsPlugin getInstance() { return instance; }
    public ConfigManager config() { return configManager; }
    public MessagesManager messages() { return messagesManager; }
    public DuelWorldManager worlds() { return worldManager; }
    public DuelManager duels() { return duelManager; }
    public ArenaManager arenas() { return arenaManager; }
    public SnapshotManager snapshots() { return snapshotManager; }
    public KitManager kits() { return kitManager; }
    public PlayerKitManager playerKits() { return playerKitManager; }
    public RewardManager rewards() { return rewardManager; }
    public StatsManager stats() { return statsManager; }
    public DatabaseManager database() { return databaseManager; }
    public LeaderboardManager leaderboards() { return leaderboardManager; }
    public PartyManager parties() { return partyManager; }
    public PartyItemManager partyItems() { return partyItemManager; }
    public PartyMatchSetupManager partyMatchSetups() { return partyMatchSetupManager; }
}