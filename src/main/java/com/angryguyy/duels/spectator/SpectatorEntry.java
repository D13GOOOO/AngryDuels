package com.angryguyy.duels.spectator;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a single active spectator of a duel session.
 *
 * <p>Each entry records the leader of the watched session, the id of
 * the arena the session is running on, the anchor location used by the
 * listener to enforce the movement radius, and the instant the
 * spectator joined.</p>
 *
 * <p>The previous state of the viewer — position, gamemode, inventory
 * — is not stored here: it is captured and restored by the snapshot
 * manager, so this record only keeps the metadata needed to keep the
 * spectator attached to the correct session and bounded inside the
 * arena.</p>
 *
 * @param viewer            uuid of the spectator
 * @param sessionLeaderUuid uuid of the leader of the watched session
 * @param arenaId           id of the arena the session is running on
 * @param spectatorSpawn    anchor used by the radius check; may be
 *                          {@code null} if the arena has no spawn
 * @param enteredAt         instant the spectator joined
 */
public record SpectatorEntry(
        UUID viewer,
        UUID sessionLeaderUuid,
        String arenaId,
        @Nullable Location spectatorSpawn,
        Instant enteredAt
) {
}