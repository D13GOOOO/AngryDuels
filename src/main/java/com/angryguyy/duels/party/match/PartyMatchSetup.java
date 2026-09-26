package com.angryguyy.duels.party.match;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Captured configuration for a party match that has not started yet.
 *
 * <p>Created when the leader completes the setup GUI, consumed by the
 * matchmaking layer once it is implemented. Until then, instances are
 * stored in memory by {@code PartyMatchSetupManager} so the leader can
 * inspect or replace their pending selection.</p>
 *
 * @param partyId     id of the party that owns the setup
 * @param leaderUuid  uuid of the leader who configured it
 * @param type        chosen fight mode
 * @param kitId       chosen kit id, or {@code null} for no kit
 * @param createdAt   instant the setup was completed
 */
public record PartyMatchSetup(
        long partyId,
        UUID leaderUuid,
        PartyMatchType type,
        @Nullable String kitId,
        Instant createdAt
) {
}