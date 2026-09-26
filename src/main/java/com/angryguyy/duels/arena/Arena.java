package com.angryguyy.duels.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single duel arena instance.
 *
 * <p>An arena is defined by a unique identifier, the world it belongs
 * to, and two sets of spawn points. The legacy {@code spawn1} and
 * {@code spawn2} fields are used for 1v1 duels. The additional
 * {@code team1Spawns} and {@code team2Spawns} lists are used for team
 * fights and hold one entry per player in each team. When a team list
 * is empty, the legacy spawn of the matching side is used as a single
 * point fallback, so existing arenas remain usable for team fights
 * without configuration changes.</p>
 *
 * <p>Arena also exposes a spectator spawn. When not configured
 * explicitly, {@link #getEffectiveSpectatorSpawn()} derives a point
 * above the midpoint between the two legacy spawns, so the feature
 * works out of the box.</p>
 *
 * <p>An arena also tracks its occupancy state, which is toggled by the
 * {@link ArenaManager} when a duel is assigned to or released from
 * it.</p>
 *
 * <p>Instances are mutable and are not thread-safe. All access must
 * happen on the Bukkit main thread.</p>
 */
public class Arena {

    /** Vertical offset applied to the derived spectator spawn. */
    private static final double DERIVED_SPECTATOR_HEIGHT = 10.0;

    private final String id;
    private final World world;
    private Location spawn1;
    private Location spawn2;
    private @Nullable Location spectatorSpawn;
    private final List<Location> team1Spawns = new ArrayList<>();
    private final List<Location> team2Spawns = new ArrayList<>();
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
     * @return arena id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the world this arena is bound to.
     *
     * @return arena world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Returns the first legacy spawn point.
     *
     * @return first spawn location
     */
    public Location getSpawn1() {
        return spawn1;
    }

    /**
     * Returns the second legacy spawn point.
     *
     * @return second spawn location
     */
    public Location getSpawn2() {
        return spawn2;
    }

    /**
     * Updates the first legacy spawn point.
     *
     * @param spawn1 new first spawn location
     */
    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    /**
     * Updates the second legacy spawn point.
     *
     * @param spawn2 new second spawn location
     */
    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    /**
     * Returns the configured spectator spawn, if any.
     *
     * @return spectator spawn, or {@code null}
     */
    public @Nullable Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    /**
     * Sets the spectator spawn.
     *
     * @param spectatorSpawn new location, or {@code null} to clear
     */
    public void setSpectatorSpawn(@Nullable Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    /**
     * Returns the spectator spawn to use for this arena.
     *
     * <p>Falls back to a point above the midpoint of the two legacy
     * spawns when no explicit spectator spawn is set, so spectator mode
     * works even on arenas that have never been configured for it.</p>
     *
     * @return spectator spawn, or {@code null} if the arena has no
     *         usable spawn at all
     */
    public Location getEffectiveSpectatorSpawn() {
        if (spectatorSpawn != null) return spectatorSpawn.clone();
        if (spawn1 != null && spawn2 != null) {
            double x = (spawn1.getX() + spawn2.getX()) / 2.0;
            double y = Math.max(spawn1.getY(), spawn2.getY()) + DERIVED_SPECTATOR_HEIGHT;
            double z = (spawn1.getZ() + spawn2.getZ()) / 2.0;
            return new Location(world, x, y, z, 0f, 45f);
        }
        return spawn1 != null ? spawn1.clone() : null;
    }

    /**
     * Returns an unmodifiable view of the team 1 spawn list.
     *
     * @return team 1 spawn list
     */
    public List<Location> getTeam1Spawns() {
        return Collections.unmodifiableList(team1Spawns);
    }

    /**
     * Returns an unmodifiable view of the team 2 spawn list.
     *
     * @return team 2 spawn list
     */
    public List<Location> getTeam2Spawns() {
        return Collections.unmodifiableList(team2Spawns);
    }

    /**
     * Adds a team spawn point.
     *
     * @param team 1 or 2
     * @param loc  spawn location to add
     */
    public void addTeamSpawn(int team, Location loc) {
        spawnsOf(team).add(loc);
    }

    /**
     * Removes a team spawn point by its one-based index.
     *
     * @param team  1 or 2
     * @param index one-based index of the spawn to remove
     * @return the removed location, or {@code null} if the index was
     *         out of range
     */
    public Location removeTeamSpawn(int team, int index) {
        List<Location> list = spawnsOf(team);
        if (index < 1 || index > list.size()) return null;
        return list.remove(index - 1);
    }

    /**
     * Removes every team spawn point of a team.
     *
     * @param team 1 or 2
     */
    public void clearTeamSpawns(int team) {
        spawnsOf(team).clear();
    }

    /**
     * Checks whether at least one spawn has been defined for both
     * teams.
     *
     * @return {@code true} if both team lists are non-empty
     */
    public boolean hasBothTeamSpawns() {
        return !team1Spawns.isEmpty() && !team2Spawns.isEmpty();
    }

    /**
     * Returns the spawns to use for a team, falling back to the legacy
     * single spawn if the team list is empty.
     *
     * @param team 1 or 2
     * @return list of spawns, never empty if the legacy spawn is set
     */
    public List<Location> getEffectiveTeamSpawns(int team) {
        List<Location> list = spawnsOf(team);
        if (!list.isEmpty()) return List.copyOf(list);
        Location fallback = team == 1 ? spawn1 : spawn2;
        if (fallback == null) return List.of();
        return List.of(fallback);
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
     * @param occupied new occupancy state
     */
    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    /**
     * Returns the spawn list of a team.
     *
     * @param team 1 or 2
     * @return mutable team spawn list
     * @throws IllegalArgumentException if the team value is not 1 or 2
     */
    private List<Location> spawnsOf(int team) {
        if (team == 1) return team1Spawns;
        if (team == 2) return team2Spawns;
        throw new IllegalArgumentException("Team must be 1 or 2");
    }
}