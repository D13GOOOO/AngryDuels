package com.angryguyy.duels.kit;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads and exposes the kits defined in {@code kits.yml}.
 *
 * <p>Kits are stored both by their primary id and, for lookup, through
 * a secondary alias index. The manager is the single access point for
 * resolving a kit by id or alias, and it owns the mapping between the
 * YAML structure and the {@link Kit} instances used at runtime.</p>
 *
 * <p>The manager never mutates kits after loading. A reload is
 * performed by calling {@link #load()} again, which discards any
 * previously loaded kit and re-reads the file from disk. This makes it
 * safe to reload at runtime as long as no duel is currently applying a
 * kit.</p>
 *
 * <p>Instances are not thread-safe; all access must happen on the
 * Bukkit main thread.</p>
 */
public class KitManager {

    private final DuelsPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<String, String> aliasIndex = new HashMap<>();
    private final File file;

    /**
     * Creates a new kit manager.
     *
     * @param plugin owning plugin, used for the data folder and logging
     */
    public KitManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    /**
     * Loads the kits from {@code kits.yml}.
     *
     * <p>If the file does not exist, the bundled default resource is
     * written to the plugin data folder first. Malformed kits are
     * logged and skipped individually, so that one invalid entry does
     * not prevent the rest of the file from loading.</p>
     *
     * <p>If two kits declare the same alias, the second declaration
     * wins and a warning is logged. Ids are unique by construction
     * because they are the keys of the top-level section.</p>
     */
    public void load() {
        kits.clear();
        aliasIndex.clear();

        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("kits");
        if (root == null) {
            Log.info("No kits configured.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                Kit kit = parse(id, sec);
                if (kit != null) {
                    kits.put(kit.getId(), kit);
                    for (String alias : kit.getAliases()) {
                        String lower = alias.toLowerCase(Locale.ROOT);
                        String previous = aliasIndex.put(lower, kit.getId());
                        if (previous != null && !previous.equals(kit.getId())) {
                            Log.warn("Alias '%s' of kit '%s' overrides the same alias on kit '%s'.",
                                    alias, kit.getId(), previous);
                        }
                    }
                }
            } catch (Exception e) {
                Log.error(e, "Failed to load kit '%s'", id);
            }
        }
        Log.info("Loaded %d kit(s).", kits.size());
    }

    /**
     * Parses a single kit from its configuration section.
     *
     * @param id  kit id
     * @param sec configuration section
     * @return the parsed kit, or {@code null} if the section lacks the
     *         required {@code slots} node
     */
    private Kit parse(String id, ConfigurationSection sec) {
        String displayName = sec.getString("display-name", id);
        String permission = sec.getString("permission", "duels.kit." + id);
        List<String> aliases = sec.getStringList("aliases");

        ConfigurationSection slots = sec.getConfigurationSection("slots");
        if (slots == null) {
            Log.warn("Kit '%s' has no slots.", id);
            return null;
        }

        Map<Integer, ItemStack> items = new TreeMap<>();
        for (String key : slots.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                Log.warn("Kit '%s' has non-numeric slot '%s'.", id, key);
                continue;
            }
            ConfigurationSection itemSec = slots.getConfigurationSection(key);
            if (itemSec == null) continue;
            try {
                ItemStack item = ItemSerializer.fromMap(itemSec.getValues(false));
                items.put(slot, item);
            } catch (Exception e) {
                Log.error(e, "Failed to parse item at slot %d of kit '%s'", slot, id);
            }
        }

        ItemStack icon = parseIcon(id, sec, items);
        return new Kit(id, displayName, permission, aliases, items, icon);
    }

    /**
     * Parses the GUI icon for a kit.
     *
     * <p>If an {@code icon} block is present, it is parsed with the
     * same format as regular kit items. Otherwise, the item in slot 0
     * is used as a fallback. If the kit has no items at all, a barrier
     * is used as the last resort.</p>
     *
     * @param id    kit id, used for logging
     * @param sec   kit configuration section
     * @param items already parsed kit items
     * @return the icon item stack
     */
    private ItemStack parseIcon(String id, ConfigurationSection sec, Map<Integer, ItemStack> items) {
        ConfigurationSection iconSec = sec.getConfigurationSection("icon");
        if (iconSec != null) {
            try {
                return ItemSerializer.fromMap(iconSec.getValues(false));
            } catch (Exception e) {
                Log.error(e, "Failed to parse icon of kit '%s'; using fallback", id);
            }
        }
        ItemStack fallback = items.get(0);
        if (fallback != null) return fallback.clone();
        return new ItemStack(Material.BARRIER);
    }

    /**
     * Returns a kit by its exact id.
     *
     * @param id kit id
     * @return the kit, or {@code null} if no kit has that id
     */
    public Kit get(String id) {
        return kits.get(id);
    }

    /**
     * Resolves a kit by id or alias.
     *
     * <p>The lookup is exact on ids and case-insensitive on aliases,
     * to avoid surprising collisions on the primary identifier. If the
     * name matches nothing, {@code null} is returned; callers are
     * expected to translate that into a user-facing error.</p>
     *
     * @param name id or alias
     * @return the matching kit, or {@code null}
     */
    public Kit resolve(String name) {
        if (name == null) return null;
        Kit direct = kits.get(name);
        if (direct != null) return direct;
        String target = aliasIndex.get(name.toLowerCase(Locale.ROOT));
        return target != null ? kits.get(target) : null;
    }

    /**
     * Returns all loaded kits.
     *
     * <p>The returned collection is a live view of the internal map
     * and preserves declaration order. It should only be iterated, not
     * mutated.</p>
     *
     * @return collection of kits in declaration order
     */
    public Collection<Kit> all() {
        return kits.values();
    }
}