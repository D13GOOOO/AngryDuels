package com.angryguyy.duels.party.match;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory store of pending party match setups.
 *
 * <p>Keeps at most one setup per party. When a party is disbanded, the
 * caller should invoke {@link #clear(long)} to release the entry.</p>
 *
 * <p>This class exists as a stepping stone: once the matchmaking layer
 * is implemented, the manager will be called by the fight GUI listener
 * with the completed setup, and it will hand the request over to the
 * duel manager.</p>
 */
public class PartyMatchSetupManager {

    private final Map<Long, PartyMatchSetup> setups = new HashMap<>();

    /**
     * Stores a setup, replacing any previous one for the same party.
     *
     * @param setup the setup to store
     */
    public void set(PartyMatchSetup setup) {
        setups.put(setup.partyId(), setup);
    }

    /**
     * Returns the pending setup for a party, if any.
     *
     * @param partyId party id
     * @return setup, or {@code null}
     */
    public PartyMatchSetup get(long partyId) {
        return setups.get(partyId);
    }

    /**
     * Removes the pending setup for a party.
     *
     * @param partyId party id
     */
    public void clear(long partyId) {
        setups.remove(partyId);
    }

    /**
     * Creates and stores a new setup in one call.
     *
     * @param partyId    party id
     * @param leaderUuid uuid of the leader who configured it
     * @param type       chosen fight mode
     * @param kitId      chosen kit id, or {@code null}
     * @return the created setup
     */
    public PartyMatchSetup create(long partyId, java.util.UUID leaderUuid,
                                  PartyMatchType type, String kitId) {
        PartyMatchSetup setup = new PartyMatchSetup(
                partyId, leaderUuid, type, kitId, Instant.now());
        setups.put(partyId, setup);
        return setup;
    }

    /**
     * Clears every stored setup.
     */
    public void shutdown() {
        setups.clear();
    }
}