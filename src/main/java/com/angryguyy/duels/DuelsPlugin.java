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
import com.angryguyy.duels.reward.RewardManager;
import com.angryguyy.duels.snapshot.PlayerSnapshot;
import com.angryguyy.duels.snapshot.SnapshotManager;
import com.angryguyy.duels.spectator.SpectatorListener;
import com.angryguyy.duels.spectator.SpectatorManager;
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
 *
 * <p>Every subsystem is exposed through a getter returning the concrete
 * manager type. The getters are intentionally not null-checked during
 * runtime: they are only valid after {@code onEnable} has created the
 * corresponding manager, which is the case for every consumer in the
 * plugin.</p>
 */
public final class DuelsPlugin extends JavaPlugin {

    /**
     * Static instance used by {@link Log} and other classes that
     * cannot receive the plugin through dependency injection.
     */
    private static DuelsPlugin instance;

    /**
     * Typed accessor for {@code config.yml}.
     */
    private ConfigManager configManager;

    /**
     * Loader and renderer for {@code messages.yml}.
     */
    private MessagesManager messagesManager;

    /**
     * Owner of the dedicated duel world.
     */
    private DuelWorldManager worldManager;

    /**
     * Central coordinator for duels and requests.
     */
    private DuelManager duelManager;

    /**
     * Registry of duel arenas.
     */
    private ArenaManager arenaManager;

    /**
     * Player state snapshots captured before duels.
     */
    private SnapshotManager snapshotManager;

    /**
     * Registry of kit definitions.
     */
    private KitManager kitManager;

    /**
     * Per-player kit layout overrides.
     */
    private PlayerKitManager playerKitManager;

    /**
     * Reward dispatcher for winners.
     */
    private RewardManager rewardManager;

    /**
     * MySQL pool, schema bootstrap and retry queue.
     */
    private DatabaseManager databaseManager;

    /**
     * Read and write access to duel statistics.
     */
    private StatsManager statsManager;

    /**
     * Cached leaderboard snapshots per category.
     */
    private LeaderboardManager leaderboardManager;

    /**
     * Persistent parties and invitations.
     */
    private PartyManager partyManager;

    /**
     * Hotbar items given to party leaders.
     */
    private PartyItemManager partyItemManager;

    /**
     * Spectator mode for active duels.
     */
    private SpectatorManager spectatorManager;

    /**
     * Called by the server when the plugin is enabled.
     *
     * <p>Managers are created in dependency order. The database is
     * initialized after the world, arenas, kits and rewards, and the
     * duel and spectator managers are created last because they depend
     * on the snapshot manager.</p>
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

        this.snapshotManager = new SnapshotManager(this);
        snapshotManager.loadAll();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.angryguyy.duels.hook.AngryDuelsExpansion(this).register();
            Log.info("PlaceholderAPI expansion registered.");
        }

        this.duelManager = new DuelManager(this);
        duelManager.start();

        this.spectatorManager = new SpectatorManager(this);
        spectatorManager.start();

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
     * <p>Subsystems are torn down in reverse dependency order: the
     * spectator and duel managers are stopped before the database pool
     * is closed, so that any session still active can release arenas
     * and restore snapshots without touching the connection pool. The
     * static instance is cleared at the end so that late calls through
     * {@link Log} fall back to the server logger.</p>
     */
    @Override
    public void onDisable() {
        if (spectatorManager != null) spectatorManager.shutdown();
        if (leaderboardManager != null) leaderboardManager.shutdown();
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
        getServer().getPluginManager().registerEvents(new SpectatorListener(this), this);
    }

    /**
     * Returns the static plugin instance.
     *
     * @return instance, or {@code null} when the plugin is not enabled
     */
    public static DuelsPlugin getInstance() {
        return instance;
    }

    /**
     * Returns the config manager.
     *
     * @return config manager
     */
    public ConfigManager config() {
        return configManager;
    }

    /**
     * Returns the messages manager.
     *
     * @return messages manager
     */
    public MessagesManager messages() {
        return messagesManager;
    }

    /**
     * Returns the duel world manager.
     *
     * @return world manager
     */
    public DuelWorldManager worlds() {
        return worldManager;
    }

    /**
     * Returns the duel manager.
     *
     * @return duel manager
     */
    public DuelManager duels() {
        return duelManager;
    }

    /**
     * Returns the arena manager.
     *
     * @return arena manager
     */
    public ArenaManager arenas() {
        return arenaManager;
    }

    /**
     * Returns the snapshot manager.
     *
     * @return snapshot manager
     */
    public SnapshotManager snapshots() {
        return snapshotManager;
    }

    /**
     * Returns the kit manager.
     *
     * @return kit manager
     */
    public KitManager kits() {
        return kitManager;
    }

    /**
     * Returns the player kit manager.
     *
     * @return player kit manager
     */
    public PlayerKitManager playerKits() {
        return playerKitManager;
    }

    /**
     * Returns the reward manager.
     *
     * @return reward manager
     */
    public RewardManager rewards() {
        return rewardManager;
    }

    /**
     * Returns the stats manager.
     *
     * @return stats manager
     */
    public StatsManager stats() {
        return statsManager;
    }

    /**
     * Returns the database manager.
     *
     * @return database manager
     */
    public DatabaseManager database() {
        return databaseManager;
    }

    /**
     * Returns the leaderboard manager.
     *
     * @return leaderboard manager
     */
    public LeaderboardManager leaderboards() {
        return leaderboardManager;
    }

    /**
     * Returns the party manager.
     *
     * @return party manager
     */
    public PartyManager parties() {
        return partyManager;
    }

    /**
     * Returns the party item manager.
     *
     * @return party item manager
     */
    public PartyItemManager partyItems() {
        return partyItemManager;
    }

    /**
     * Returns the spectator manager.
     *
     * @return spectator manager
     */
    public SpectatorManager spectators() {
        return spectatorManager;
    }
}