package com.angryguyy.duels.config;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed accessor for {@code config.yml}.
 *
 * <p>This class exists to keep every raw configuration path in a single
 * place: all other components query settings through the getters
 * declared here, never by calling {@link FileConfiguration#getString}
 * or similar methods directly. This makes it possible to rename a
 * config path without touching the rest of the codebase.</p>
 *
 * <p>Every getter returns a sane default value if the corresponding key
 * is missing, so the plugin remains functional even with a partially
 * populated or outdated config file.</p>
 *
 * <p>Instances are not thread-safe; all access must happen on the
 * Bukkit main thread.</p>
 */
public class ConfigManager {

    private final DuelsPlugin plugin;

    /**
     * Creates the config manager and ensures a valid {@code config.yml}
     * exists on disk.
     *
     * <p>If the file is missing, the default resource bundled with the
     * plugin is written to the data folder. Missing keys are then copied
     * from the bundled defaults so that new options added in a future
     * version are always available without wiping user edits.</p>
     *
     * @param plugin owning plugin instance
     */
    public ConfigManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    /**
     * Reloads the configuration from disk.
     *
     * <p>After this call, every getter reflects the values currently
     * stored in the file. Callers are responsible for refreshing any
     * component that cached a config value.</p>
     */
    public void reload() {
        plugin.reloadConfig();
    }

    /**
     * Exposes the underlying {@link FileConfiguration}.
     *
     * <p>This method is intended for internal use by managers that need
     * to read or write arbitrary sections (for example the arena list).
     * Prefer the typed getters whenever possible.</p>
     *
     * @return the raw config object
     */
    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    /**
     * Indicates whether verbose debug logging is enabled.
     *
     * @return {@code true} if the {@code settings.debug} flag is set
     */
    public boolean debug() {
        return raw().getBoolean("settings.debug", false);
    }

    /**
     * Returns the configured language code.
     *
     * @return language identifier, defaulting to {@code en}
     */
    public String language() {
        return raw().getString("settings.language", "en");
    }

    /**
     * Returns how long a duel request stays valid before expiring.
     *
     * @return timeout in seconds
     */
    public int requestTimeoutSeconds() {
        return raw().getInt("duel.request-timeout-seconds", 30);
    }

    /**
     * Returns the duration of the pre-duel countdown.
     *
     * @return countdown duration in seconds
     */
    public int countdownSeconds() {
        return raw().getInt("duel.countdown-seconds", 5);
    }

    /**
     * Returns the cooldown between two outgoing duel requests.
     *
     * @return cooldown in seconds
     */
    public int cooldownSeconds() {
        return raw().getInt("duel.cooldown-seconds", 10);
    }

    /**
     * Indicates whether players sharing the same IP are forbidden from
     * duelling each other.
     *
     * @return {@code true} if same-IP duels are blocked
     */
    public boolean preventSameIp() {
        return raw().getBoolean("duel.prevent-same-ip", false);
    }

    /**
     * Returns the name of the dedicated duel world.
     *
     * @return world name
     */
    public String worldName() {
        return raw().getString("world.name", "duels_world");
    }

    /**
     * Indicates whether the plugin should generate the duel world when
     * it is missing.
     *
     * @return {@code true} if the world should be auto-created
     */
    public boolean autoCreateWorld() {
        return raw().getBoolean("world.auto-create", true);
    }

    /**
     * Returns the configured world generator type.
     *
     * @return generator identifier, currently either {@code void} or
     *         {@code normal}
     */
    public String worldGenerator() {
        return raw().getString("world.generator", "void");
    }

    /**
     * Returns the configured storage backend type.
     *
     * @return storage identifier, currently only {@code mysql}
     */
    public String storageType() {
        return raw().getString("storage.type", "mysql");
    }

    /**
     * Returns the MySQL host.
     *
     * @return hostname or IP address
     */
    public String mysqlHost() {
        return raw().getString("storage.mysql.host", "localhost");
    }

    /**
     * Returns the MySQL port.
     *
     * @return TCP port
     */
    public int mysqlPort() {
        return raw().getInt("storage.mysql.port", 3306);
    }

    /**
     * Returns the MySQL database name.
     *
     * @return database name
     */
    public String mysqlDatabase() {
        return raw().getString("storage.mysql.database", "duels");
    }

    /**
     * Returns the MySQL username.
     *
     * @return username
     */
    public String mysqlUsername() {
        return raw().getString("storage.mysql.username", "root");
    }

    /**
     * Returns the MySQL password.
     *
     * @return password, possibly empty
     */
    public String mysqlPassword() {
        return raw().getString("storage.mysql.password", "");
    }

    /**
     * Returns the configured connection pool size.
     *
     * @return maximum number of pooled connections
     */
    public int mysqlPoolSize() {
        return raw().getInt("storage.mysql.pool-size", 10);
    }

    /**
     * Indicates whether SSL should be used for the MySQL connection.
     *
     * @return {@code true} if SSL is enabled
     */
    public boolean mysqlUseSsl() {
        return raw().getBoolean("storage.mysql.use-ssl", false);
    }
}