package com.angryguyy.duels.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a single duel arena instance.
 *
 * <p>An arena is defined by a unique identifier, the world it belongs to,
 * and two spawn points used to place the two duelists facing each other
 * at the start of a match. An arena is also aware of its current
 * occupancy state, which is toggled by the {@link ArenaManager} when a
 * duel is assigned to or released from it.</p>
 *
 * <p>Bounds are intentionally not enforced: the arena is currently a
 * logical grouping of spawn points rather than a protected region. A
 * future iteration may add cuboid boundaries and block protection.</p>
 *
 * <p>Instances are mutable and are not thread-safe. All access must
 * happen on the Bukkit main thread.</p>
 */
public class Arena {

    private final String id;
    private final World world;
    private Location spawn1;
    private Location spawn2;
    private boolean occupied;

    /**
     * Creates a new arena.
     *
     * @param id     unique identifier of the arena, used as a config key
     * @param world  world the arena belongs to; must not be {@code null}
     * @param spawn1 first spawn point, typically for the first duelist
     * @param spawn2 second spawn point, typically for the second duelist
     */
    public Arena(String id, World world, Location spawn1, Location spawn2) {
        this.id = id;
        this.world = world;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
    }

    /**
     * Returns the unique identifier of this arena.
     *
     * @return the arena id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the world this arena is bound to.
     *
     * @return the arena world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Returns the first spawn point.
     *
     * @return location of the first spawn
     */
    public Location getSpawn1() {
        return spawn1;
    }

    /**
     * Returns the second spawn point.
     *
     * @return location of the second spawn
     */
    public Location getSpawn2() {
        return spawn2;
    }

    /**
     * Updates the first spawn point.
     *
     * @param spawn1 new location for the first spawn
     */
    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    /**
     * Updates the second spawn point.
     *
     * @param spawn2 new location for the second spawn
     */
    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    /**
     * Checks whether this arena is currently free and can be assigned
     * to a new duel.
     *
     * @return {@code true} if the arena is not occupied
     */
    public boolean isAvailable() {
        return !occupied;
    }

    /**
     * Checks whether this arena is currently in use by a duel.
     *
     * @return {@code true} if the arena is occupied
     */
    public boolean isOccupied() {
        return occupied;
    }

    /**
     * Sets the occupancy state of this arena.
     *
     * <p>This method is normally called by {@link ArenaManager} and
     * should not be invoked directly from command handlers.</p>
     *
     * @param occupied new occupancy state
     */
    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }
}