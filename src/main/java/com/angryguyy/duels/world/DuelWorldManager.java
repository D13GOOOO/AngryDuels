package com.angryguyy.duels.world;

import java.io.File;
import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Owns the dedicated duel world and its gameplay rules.
 *
 * <p>The manager is responsible for three things:</p>
 * <ul>
 *     <li>Locating the duel world at startup, or generating a new one
 *     when the {@code world.auto-create} flag is enabled and no world
 *     with the configured name exists.</li>
 *     <li>Applying a curated set of gamerules and difficulty settings
 *     that make the world suitable for PvP duels: no mobs, no weather
 *     cycles, no fire spread, immediate respawn, and so on.</li>
 *     <li>Exposing the world instance and quick predicates so that
 *     other components can detect whether a given location belongs to
 *     the duel world without holding a direct reference.</li>
 * </ul>
 *
 * <p>Only one duel world is supported. The reference is captured once
 * during {@link #init()} and reused for the whole plugin lifetime.</p>
 *
 * <p>Instances are not thread-safe; all access must happen on the
 * Bukkit main thread.</p>
 */
public class DuelWorldManager {

    private final DuelsPlugin plugin;
    private World duelWorld;

    /**
     * Creates a new world manager.
     *
     * @param plugin owning plugin, used for config access and logging
     */
    public DuelWorldManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the duel world.
     *
     * <p>If a world with the configured name is already loaded, it is
     * reused. Otherwise the manager checks whether the world exists on
     * disk and either loads it or creates a new one, depending on the
     * auto-create flag. In every case the curated gamerules are applied
     * afterwards.</p>
     *
     * <p>Bukkit does not automatically load worlds that are not declared
     * in {@code bukkit.yml}, so a previously created duel world must be
     * loaded explicitly here.</p>
     */
    public void init() {
        String name = plugin.config().worldName();
        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        boolean existsOnDisk = worldFolder.exists()
                && new File(worldFolder, "level.dat").exists();

        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            this.duelWorld = existing;
            Log.info("Found loaded duel world: %s", name);
        } else if (existsOnDisk) {
            this.duelWorld = load(name);
            if (duelWorld != null) {
                Log.info("Loaded existing duel world from disk: %s", name);
            } else {
                Log.error("Failed to load duel world '%s'", name);
                return;
            }
        } else if (plugin.config().autoCreateWorld()) {
            this.duelWorld = create(name);
            if (duelWorld != null) {
                Log.info("Created new duel world: %s", name);
            } else {
                Log.error("Failed to create duel world '%s'", name);
                return;
            }
        } else {
            Log.warn("Duel world '%s' not found and auto-create is disabled.", name);
            return;
        }

        applyRules(duelWorld);
    }

    /**
     * Loads an existing duel world from disk.
     *
     * @param name name of the world to load
     * @return the loaded world, or {@code null} if loading failed
     */
    private World load(String name) {
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        return creator.createWorld();
    }

    /**
     * Generates a brand new duel world.
     *
     * @param name name of the world to create
     * @return the created world, or {@code null} if generation failed
     */
    private World create(String name) {
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generator(new VoidChunkGenerator());
        World world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 65, 0);
        }
        return world;
    }

    /**
     * Applies the gamerules and difficulty used by the duel world.
     *
     * <p>The rules are tuned for a controlled PvP environment: mobs
     * are disabled, weather and time are frozen, fire spread is turned
     * off, and respawns are immediate so that a duel can end cleanly
     * without vanilla side effects leaking in.</p>
     *
     * @param world world to configure; must not be {@code null}
     */
    private void applyRules(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.RAIDS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setDifficulty(Difficulty.NORMAL);
    }

    /**
     * Returns the duel world.
     *
     * @return the duel world, or {@code null} if it has not been
     *         initialized
     */
    public World getWorld() {
        return duelWorld;
    }

    /**
     * Checks whether the duel world is available.
     *
     * @return {@code true} if the duel world has been loaded or created
     */
    public boolean isReady() {
        return duelWorld != null;
    }

    /**
     * Checks whether a given world is the duel world.
     *
     * @param world world to test; may be {@code null}
     * @return {@code true} if the given world matches the duel world
     */
    public boolean isDuelWorld(World world) {
        return duelWorld != null && duelWorld.equals(world);
    }
}