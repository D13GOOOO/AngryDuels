package com.angryguyy.duels.config;

import com.angryguyy.duels.DuelsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code messages.yml} and renders MiniMessage strings into
 * Adventure {@link Component} instances.
 *
 * <p>All user-facing text flows through this class. Messages are stored
 * as raw MiniMessage tags in the YAML file and are parsed on demand,
 * which allows administrators to edit colours, formatting, hover and
 * click events without touching the plugin code.</p>
 *
 * <p>Placeholders are passed as a map and are inserted using
 * {@link Placeholder#unparsed(String, String)}, meaning any MiniMessage
 * tag appearing inside a placeholder value is treated as literal text.
 * This prevents players from injecting formatting through their
 * nickname or other user-controlled values.</p>
 *
 * <p>Instances are not thread-safe; all access must happen on the
 * Bukkit main thread.</p>
 */
public class MessagesManager {

    private final DuelsPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private YamlConfiguration messages;
    private Component prefix;

    /**
     * Creates the messages manager and performs an initial load.
     *
     * @param plugin owning plugin instance
     */
    public MessagesManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Reloads {@code messages.yml} from disk.
     *
     * <p>If the file does not exist in the plugin data folder, the
     * bundled default resource is written out first. The parsed prefix
     * is also refreshed as part of this call.</p>
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        this.prefix = mm.deserialize(messages.getString("prefix", ""));
    }

    /**
     * Returns the parsed message prefix.
     *
     * @return prefix component
     */
    public Component prefix() {
        return prefix;
    }

    /**
     * Returns the message stored at the given key, without the prefix.
     *
     * @param key dotted path inside {@code messages.yml}
     * @return parsed component, or a red error component if the key is
     *         missing
     */
    public Component get(String key) {
        return get(key, null);
    }

    /**
     * Returns the message stored at the given key with placeholders
     * substituted, without the prefix.
     *
     * @param key          dotted path inside {@code messages.yml}
     * @param placeholders placeholder values, or {@code null} for none
     * @return parsed component, or a red error component if the key is
     *         missing
     */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            return mm.deserialize("<red>[missing message: " + key + "]");
        }
        TagResolver.Builder builder = TagResolver.builder();
        if (placeholders != null) {
            placeholders.forEach((k, v) -> builder.resolver(Placeholder.unparsed(k, v)));
        }
        return mm.deserialize(raw, builder.build());
    }

    /**
     * Returns the message stored at the given key, prefixed.
     *
     * @param key dotted path inside {@code messages.yml}
     * @return prefixed component
     */
    public Component prefixed(String key) {
        return prefixed(key, null);
    }

    /**
     * Returns the message stored at the given key with placeholders
     * substituted, prefixed.
     *
     * @param key          dotted path inside {@code messages.yml}
     * @param placeholders placeholder values, or {@code null} for none
     * @return prefixed component
     */
    public Component prefixed(String key, Map<String, String> placeholders) {
        return prefix.append(get(key, placeholders));
    }

    /**
     * Sends a prefixed message to the given receiver.
     *
     * @param sender receiver of the message
     * @param key    dotted path inside {@code messages.yml}
     */
    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefixed(key));
    }

    /**
     * Sends a prefixed message with placeholders to the given receiver.
     *
     * @param sender       receiver of the message
     * @param key          dotted path inside {@code messages.yml}
     * @param placeholders placeholder values
     */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    /**
     * Sends every line of a message list stored at the given key.
     *
     * <p>Unlike {@link #send(CommandSender, String)}, this method does
     * not prepend the prefix, since lists are commonly used for
     * structured output such as help pages or leaderboards.</p>
     *
     * @param sender receiver of the messages
     * @param key    dotted path inside {@code messages.yml}
     */
    public void sendList(CommandSender sender, String key) {
        List<String> lines = messages.getStringList(key);
        if (lines.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>[missing message list: " + key + "]"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mm.deserialize(line));
        }
    }
}