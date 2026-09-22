package com.angryguyy.duels.util;

import com.angryguyy.duels.DuelsPlugin;

import java.util.logging.Level;

/**
 * Thin logging facade for the plugin.
 *
 * <p>All log output goes through this class so that the logger
 * reference, the debug toggle, and the message formatting are handled
 * in one place. Message arguments use the same
 * {@link String#format(String, Object...)} placeholders as the rest of
 * the plugin.</p>
 *
 * <p>Debug output is gated by the {@code settings.debug} flag in
 * {@code config.yml}. When the flag is off, {@link #debug} returns
 * immediately without touching the logger, so the guard is cheap
 * enough to leave in hot paths.</p>
 *
 * <p>The class is stateless and cannot be instantiated.</p>
 */
public final class Log {

    private Log() {
    }

    /**
     * Logs an informational message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void info(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().info(fmt(msg, args));
    }

    /**
     * Logs a warning message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void warn(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().warning(fmt(msg, args));
    }

    /**
     * Logs a severe error message.
     *
     * @param msg  message template
     * @param args optional formatting arguments
     */
    public static void error(String msg, Object... args) {
        DuelsPlugin.getInstance().getLogger().log(Level.SEVERE, fmt(msg, args));
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
        DuelsPlugin.getInstance().getLogger().log(Level.SEVERE, fmt(msg, args), t);
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
     * Formats a message template, returning it unchanged when no
     * arguments are provided.
     *
     * @param msg  message template
     * @param args formatting arguments
     * @return the formatted string
     */
    private static String fmt(String msg, Object... args) {
        return args.length == 0 ? msg : String.format(msg, args);
    }
}