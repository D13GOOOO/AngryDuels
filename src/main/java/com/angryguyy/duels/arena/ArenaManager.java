package com.angryguyy.duels.arena;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry and lifecycle manager for {@link Arena} instances.
 *
 * <p>The manager is responsible for three distinct concerns:</p>
 * <ul>
 *     <li><b>Persistence</b> — reading arenas from {@code config.yml}
 *     at startup or on reload, and writing them back after any change
 *     performed through the in-game admin commands.</li>
 *     <li><b>Allocation</b> — providing the next free arena to a duel
 *     through {@link #assign()} and returning it to the pool through
 *     {@link #release(Arena)}.</li>
 *     <li><b>Lookup</b> — exposing arenas by id or as a read-only view.</li>
 * </ul>
 *
 * <p>Arenas are stored in insertion order using a {@link LinkedHashMap}
 * so that the order shown to administrators in {@code /duel arena list}
 * matches the order in which arenas were created or loaded.</p>
 *
 * <p>This class is not thread-safe; all methods must be invoked from the
 * Bukkit main thread.</p>
 */
public class ArenaManager {

    private final DuelsPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    /**
     * Creates a new arena manager bound to the given plugin instance.
     *
     * @param plugin owning plugin, used for config access and logging
     */
    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads the arena list from {@code config.yml}.
     *
     * <p>Any arena currently held in memory is discarded before the
     * reload. If the {@code arenas} section is missing or empty, the
     * manager ends up with no registered arena and logs an informational
     * message.</p>
     *
     * <p>Arenas referencing an unloaded world or missing spawn data are
     * skipped individually so that a single malformed entry does not
     * prevent the rest of the configuration from loading.</p>
     */
    public void load() {
        arenas.clear();
        FileConfiguration cfg = plugin.config().raw();
        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) {
            Log.info("No arenas configured.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                Arena arena = parse(id, sec);
                if (arena != null) arenas.put(id, arena);
            } catch (Exception e) {
                Log.error(e, "Failed to load arena '%s'", id);
            }
        }
        Log.info("Loaded %d arena(s).", arenas.size());
    }

    /**
     * Persists the current in-memory arena list back to {@code config.yml}.
     *
     * <p>The whole {@code arenas} section is rewritten from scratch, so
     * any manual edit performed outside the plugin will be overwritten.
     * The config file is flushed to disk immediately after writing.</p>
     */
    public void save() {
        FileConfiguration cfg = plugin.config().raw();
        cfg.set("arenas", null);
        for (Arena a : arenas.values()) {
            String base = "arenas." + a.getId() + ".";
            cfg.set(base + "world", a.getWorld().getName());
            writeLoc(cfg, base + "spawn1", a.getSpawn1());
            writeLoc(cfg, base + "spawn2", a.getSpawn2());
        }
        plugin.saveConfig();
    }

    /**
     * Parses a single arena entry from its configuration section.
     *
     * @param id arena id
     * @param s  configuration section containing the arena data
     * @return the parsed {@link Arena}, or {@code null} if the section
     *         is malformed or references an unloaded world
     */
    private Arena parse(String id, ConfigurationSection s) {
        String worldName = s.getString("world");
        if (worldName == null) {
            Log.warn("Arena '%s' missing world.", id);
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Log.warn("Arena '%s' references unloaded world '%s'.", id, worldName);
            return null;
        }
        Location s1 = readLoc(s.getConfigurationSection("spawn1"), world);
        Location s2 = readLoc(s.getConfigurationSection("spawn2"), world);
        if (s1 == null || s2 == null) {
            Log.warn("Arena '%s' missing spawn1/spawn2.", id);
            return null;
        }
        return new Arena(id, world, s1, s2);
    }

    /**
     * Reads a {@link Location} from a configuration section.
     *
     * @param s     section containing {@code x}, {@code y}, {@code z},
     *              and optionally {@code yaw} and {@code pitch}
     * @param world world to bind the location to
     * @return the deserialized location, or {@code null} if the section
     *         is missing
     */
    private Location readLoc(ConfigurationSection s, World world) {
        if (s == null) return null;
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw", 0.0),
                (float) s.getDouble("pitch", 0.0));
    }

    /**
     * Writes a {@link Location} to the given config path.
     *
     * @param cfg  config to write into
     * @param path dotted path prefix, without trailing dot
     * @param loc  location to serialize
     */
    private void writeLoc(FileConfiguration cfg, String path, Location loc) {
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
    }

    /**
     * Assigns the next available arena and marks it as occupied.
     *
     * <p>Arenas are scanned in insertion order. The first one whose
     * occupancy flag is {@code false} is reserved and returned to the
     * caller, which becomes responsible for releasing it once the duel
     * ends.</p>
     *
     * @return an available arena, or {@code null} if every arena is
     *         currently occupied
     */
    public Arena assign() {
        for (Arena a : arenas.values()) {
            if (a.isAvailable()) {
                a.setOccupied(true);
                return a;
            }
        }
        return null;
    }

    /**
     * Releases a previously assigned arena, making it available again.
     *
     * @param arena arena to release; a {@code null} value is ignored
     */
    public void release(Arena arena) {
        if (arena != null) arena.setOccupied(false);
    }

    /**
     * Returns a live view of all registered arenas.
     *
     * <p>The returned collection is backed by the internal map, so
     * mutations should be performed only through the manager's public
     * methods.</p>
     *
     * @return collection of all arenas, in insertion order
     */
    public Collection<Arena> all() {
        return arenas.values();
    }

    /**
     * Looks up an arena by its unique identifier.
     *
     * @param id arena id
     * @return the matching arena, or {@code null} if none is registered
     */
    public Arena get(String id) {
        return arenas.get(id);
    }

    /**
     * Registers a new arena and immediately persists it to disk.
     *
     * @param id     unique id of the new arena
     * @param world  world the arena belongs to
     * @param spawn1 first spawn point
     * @param spawn2 second spawn point
     * @return the newly created arena
     */
    public Arena create(String id, World world, Location spawn1, Location spawn2) {
        Arena arena = new Arena(id, world, spawn1, spawn2);
        arenas.put(id, arena);
        save();
        return arena;
    }

    /**
     * Removes an arena and persists the change to disk.
     *
     * @param id id of the arena to delete
     * @return {@code true} if an arena with the given id existed and
     *         was removed, {@code false} otherwise
     */
    public boolean delete(String id) {
        Arena removed = arenas.remove(id);
        if (removed == null) return false;
        save();
        return true;
    }
}