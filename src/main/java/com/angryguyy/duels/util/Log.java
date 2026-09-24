package com.angryguyy.duels.util;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin logging facade for the plugin.
 *
 * <p>All log output goes through this class so that the logger
 * reference, the debug toggle, and the message formatting are handled
 * in one place. Message arguments use the same
 * {@link String#format(String, Object...)} placeholders as the rest of
 * the plugin.</p>
 *
 * <p>If the plugin instance is not available — for example while the
 * plugin is being disabled and the static reference has already been
 * cleared — logging falls back to the server's root logger with a
 * plugin-specific prefix, so early or late calls never throw a
 * {@link NullPointerException}.</p>
 *
 * <p>Debug output is gated by the {@code settings.debug} flag in
 * {@code config.yml}. When the flag is off, {@link #debug} returns
 * immediately without touching the logger, so the guard is cheap
 * enough to leave in hot paths.</p>
 *
 * <p>The class is stateless and cannot be instantiated.</p>
 */
public final class Log {

    private static final String FALLBACK_PREFIX = "[AngryDuels] ";

    private Log() {
    }

    /**
     * Logs an informational message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void info(String msg, Object... args) {
        logger().info(fmt(msg, args));
    }

    /**
     * Logs a warning message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void warn(String msg, Object... args) {
        logger().warning(fmt(msg, args));
    }

    /**
     * Logs a severe error message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void error(String msg, Object... args) {
        logger().log(Level.SEVERE, fmt(msg, args));
    }

    /**
     * Logs a severe error message along with a throwable.
     *
     * <p>The throwable stack trace is included in the log output, which
     * makes this overload the preferred choice inside catch blocks.</p>
     *
     * @param t    throwable to log
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void error(Throwable t, String msg, Object... args) {
        logger().log(Level.SEVERE, fmt(msg, args), t);
    }

    /**
     * Logs a debug message when debug logging is enabled.
     *
     * <p>If the plugin instance is not available (for example during
     * early startup or after shutdown), or if the {@code settings.debug}
     * flag is off, the call is a no-op.</p>
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void debug(String msg, Object... args) {
        DuelsPlugin plugin = DuelsPlugin.getInstance();
        if (plugin == null) return;
        if (!plugin.getConfig().getBoolean("settings.debug", false)) return;
        plugin.getLogger().info("[DEBUG] " + fmt(msg, args));
    }

    /**
     * Returns the logger to use for a log call.
     *
     * <p>Prefers the plugin logger when the plugin instance is
     * available, and falls back to the server root logger otherwise.</p>
     *
     * @return the active logger
     */
    private static Logger logger() {
        DuelsPlugin plugin = DuelsPlugin.getInstance();
        return plugin != null ? plugin.getLogger() : Bukkit.getLogger();
    }

    /**
     * Formats a message template, returning it unchanged when no
     * arguments are provided.
     *
     * <p>Uses {@link Locale#ROOT} so that the output of numeric
     * formatting is identical regardless of the system locale.</p>
     *
     * @param msg  message template
     * @param args formatting arguments
     * @return the formatted string
     */
    private static String fmt(String msg, Object... args) {
        return args.length == 0 ? msg : String.format(Locale.ROOT, msg, args);
    }
}