package com.angryguyy.duels.config;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed wrapper around config.yml.
 * Add getters here as the plugin grows, so other classes never touch raw paths.
 */
public class ConfigManager {

    private final DuelsPlugin plugin;

    public ConfigManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    // --- settings ---
    public boolean debug() {
        return raw().getBoolean("settings.debug", false);
    }

    public String language() {
        return raw().getString("settings.language", "en");
    }

    // --- duel ---
    public int requestTimeoutSeconds() {
        return raw().getInt("duel.request-timeout-seconds", 30);
    }

    public int countdownSeconds() {
        return raw().getInt("duel.countdown-seconds", 5);
    }

    public int cooldownSeconds() {
        return raw().getInt("duel.cooldown-seconds", 10);
    }

    public boolean preventSameIp() {
        return raw().getBoolean("duel.prevent-same-ip", false);
    }

    // --- world ---
    public String worldName() {
        return raw().getString("world.name", "duels_world");
    }

    public boolean autoCreateWorld() {
        return raw().getBoolean("world.auto-create", true);
    }

    public String worldGenerator() {
        return raw().getString("world.generator", "void");
    }

    // --- storage ---
    public String storageType() {
        return raw().getString("storage.type", "mysql");
    }

    public String mysqlHost() {
        return raw().getString("storage.mysql.host", "localhost");
    }

    public int mysqlPort() {
        return raw().getInt("storage.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return raw().getString("storage.mysql.database", "duels");
    }

    public String mysqlUsername() {
        return raw().getString("storage.mysql.username", "root");
    }

    public String mysqlPassword() {
        return raw().getString("storage.mysql.password", "");
    }

    public int mysqlPoolSize() {
        return raw().getInt("storage.mysql.pool-size", 10);
    }

    public boolean mysqlUseSsl() {
        return raw().getBoolean("storage.mysql.use-ssl", false);
    }
}