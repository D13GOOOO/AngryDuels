package com.angryguyy.duels.party;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object describing a pending party invitation.
 *
 * @param partyId   id of the party the invitee is being invited to
 * @param inviter   uuid of the player who sent the invitation
 * @param invitee   uuid of the invited player
 * @param expiresAt instant after which the invitation is no longer valid
 */
public record PartyInvite(
        long partyId,
        UUID inviter,
        UUID invitee,
        Instant expiresAt
) {

    /**
     * Checks whether the invitation has expired.
     *
     * @return {@code true} if the current time is past the expiry
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}