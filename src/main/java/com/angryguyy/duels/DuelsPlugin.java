package com.angryguyy.duels;

import com.angryguyy.duels.command.DuelsCommand;
import com.angryguyy.duels.config.ConfigManager;
import com.angryguyy.duels.config.MessagesManager;
import com.angryguyy.duels.duel.DuelManager;
import com.angryguyy.duels.listener.PlayerDeathListener;
import com.angryguyy.duels.listener.PlayerQuitListener;
import com.angryguyy.duels.util.Log;
import com.angryguyy.duels.world.DuelWorldManager;
import com.angryguyy.duels.arena.ArenaManager;
import com.angryguyy.duels.listener.DuelProtectionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class and service locator for the whole Duels plugin.
 *
 * <p>This class is responsible for the plugin lifecycle: it creates
 * every manager in the correct order during {@code onEnable}, wires up
 * commands and listeners, and tears everything down on
 * {@code onDisable}. It also exposes the managers through typed
 * accessors, so that other classes never need to reach into static
 * state or Bukkit's plugin registry to obtain them.</p>
 *
 * <p>The initialization order matters. The configuration is loaded
 * first, since every other component depends on it. The duel world
 * and arena managers follow, because the duel manager needs them to
 * start a session. The duel manager is created last, and its repeating
 * task is scheduled immediately.</p>
 *
 * <p>Only one instance of the plugin exists per server; the static
 * {@link #getInstance()} accessor is provided for legacy code and for
 * utilities such as {@code Log} that cannot easily receive a
 * reference. New code should prefer the typed accessors.</p>
 */
public final class DuelsPlugin extends JavaPlugin {

    private static DuelsPlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private DuelWorldManager worldManager;
    private DuelManager duelManager;
    private ArenaManager arenaManager;

    /**
     * Called by the server when the plugin is enabled.
     *
     * <p>Instantiates all managers in dependency order, registers the
     * command executor and the event listeners, and logs a startup
     * banner. Any exception thrown here will prevent the plugin from
     * loading and is left uncaught on purpose so that configuration or
     * environment problems surface immediately.</p>
     */
    @Override
    public void onEnable() {
        instance = this;
        Log.info("RealisticDuels v%s enabling...", getPluginMeta().getVersion());

        this.configManager = new ConfigManager(this);
        this.messagesManager = new MessagesManager(this);
        this.worldManager = new DuelWorldManager(this);
        worldManager.init();
        this.arenaManager = new ArenaManager(this);
        arenaManager.load();

        this.duelManager = new DuelManager(this);
        duelManager.start();

        registerCommands();
        registerListeners();

        Log.info("RealisticDuels enabled.");
    }

    /**
     * Called by the server when the plugin is disabled.
     *
     * <p>Shuts down the duel manager, which cancels its scheduled tasks
     * and terminates any active session. Other managers do not hold
     * resources that need explicit cleanup at this time.</p>
     */
    @Override
    public void onDisable() {
        if (duelManager != null) duelManager.shutdown();
        Log.info("RealisticDuels disabled.");
        instance = null;
    }

    /**
     * Registers the {@code /duels} command and its tab completer.
     *
     * <p>If the command is not declared in {@code plugin.yml}, an error
     * is logged and the registration is skipped, so the plugin can
     * still load in a misconfigured environment.</p>
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
        getServer().getPluginManager().registerEvents(new DuelProtectionListener(this), this);
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the plugin instance, or {@code null} if called outside
     *         the enable/disable window
     */
    public static DuelsPlugin getInstance() {
        return instance;
    }

    /**
     * Returns the configuration manager.
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
}