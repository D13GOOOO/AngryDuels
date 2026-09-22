package com.angryguyy.duels.util;

import com.angryguyy.duels.DuelsPlugin;

import java.util.logging.Level;

/**
 * Small logging helper with formatted messages.
 * Debug output is gated by the "settings.debug" flag in config.yml.
 */
public final class Log {

    private Log() {}

    public static void info(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().info(fmt(msg, args));
    }

    public static void warn(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().warning(fmt(msg, args));
    }

    public static void error(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().log(Level.SEVERE, fmt(msg, args));
    }

    public static void error(Throwable t, String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().log(Level.SEVERE, fmt(msg, args), t);
    }

    public static void debug(String msg, Object... args) {
        DuelsPlugin plugin = DuelsPlugin.getInstance();
        if (plugin == null) return;
        if (!plugin.getConfig().getBoolean("settings.debug", false)) return;
        plugin.getLogger().info("[DEBUG] " + fmt(msg, args));
    }

    private static String fmt(String msg, Object... args) {
        return args.length == 0 ? msg : String.format(msg, args);
    }
}