package com.angryguyy.duels.party;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.PartyCreateEvent;
import com.angryguyy.duels.event.PartyDisbandEvent;
import com.angryguyy.duels.event.PartyJoinEvent;
import com.angryguyy.duels.event.PartyLeaveEvent;
import com.angryguyy.duels.stats.DatabaseManager;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages persistent parties and their invitations.
 *
 * <p>Parties and invitations live in three database tables. The manager
 * keeps a full in-memory cache of both, which is loaded on startup and
 * kept in sync with every mutation. Because party operations are
 * relatively rare, the cache is refreshed from the source of truth
 * (the database) only at startup; at runtime the cache is authoritative
 * and every change is mirrored asynchronously.</p>
 *
 * <p>All public methods must be invoked on the Bukkit main thread. The
 * database writes they trigger are asynchronous.</p>
 *
 * <p>A party is automatically disbanded when its last member leaves,
 * either voluntarily or via kick. The disband is persisted by deleting
 * the row in {@code duels_parties}, and the foreign key cascades clean
 * up members and invitations.</p>
 *
 * <p>The public flag is stored on the {@link Party} instance itself and
 * is not persisted. After a server restart every party reverts to
 * private; if persisted visibility is required, an {@code is_public}
 * column can be added to the parties table.</p>
 */
public class PartyManager {

    /** Duration of a pending invitation, in seconds. */
    private static final int INVITE_TIMEOUT_SECONDS = 30;

    /** Interval between invitation expiry checks, in ticks. */
    private static final long INVITE_SWEEP_INTERVAL_TICKS = 20L * 30L;

    private final DuelsPlugin plugin;
    private final DatabaseManager database;

    private final Map<Long, Party> parties = new HashMap<>();
    private final Map<UUID, Long> playerToParty = new HashMap<>();
    private final Map<UUID, PartyInvite> invitesByInvitee = new HashMap<>();

    private final AtomicLong nextPartyId = new AtomicLong(1);

    private BukkitTask sweepTask;

    /**
     * Creates a new party manager.
     *
     * @param plugin   owning plugin
     * @param database underlying database manager
     */
    public PartyManager(DuelsPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Loads all parties, members and pending invitations from the
     * database into memory.
     *
     * <p>Called once on startup. If the database is unavailable, the
     * manager stays empty and every public method becomes a no-op until
     * the plugin is restarted.</p>
     */
    public void load() {
        if (!database.isReady()) {
            Log.warn("Party system disabled: database not ready.");
            return;
        }

        try (Connection conn = database.getDataSource().getConnection()) {
            long maxId = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, leader_uuid, created_at FROM duels_parties");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    parties.put(id, new Party(id, leader, createdAt));
                    if (id > maxId) maxId = id;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT party_id, uuid, joined_at FROM duels_party_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long partyId = rs.getLong("party_id");
                    Party party = parties.get(partyId);
                    if (party == null) continue;
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Instant joinedAt = rs.getTimestamp("joined_at").toInstant();
                    party.addMember(uuid, joinedAt);
                    playerToParty.put(uuid, partyId);
                }
            }

            Timestamp now = Timestamp.from(Instant.now());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT party_id, inviter, invitee, expires_at "
                            + "FROM duels_party_invites WHERE expires_at > ?")) {
                ps.setTimestamp(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long partyId = rs.getLong("party_id");
                        UUID inviter = UUID.fromString(rs.getString("inviter"));
                        UUID invitee = UUID.fromString(rs.getString("invitee"));
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                        invitesByInvitee.put(invitee,
                                new PartyInvite(partyId, inviter, invitee, expiresAt));
                    }
                }
            }

            nextPartyId.set(maxId + 1);
            Log.info("Loaded %d party(ies), %d pending invite(s).",
                    parties.size(), invitesByInvitee.size());
        } catch (SQLException e) {
            Log.error(e, "Failed to load parties from database");
        }

        startInviteSweeper();
    }

    /**
     * Stops the invitation sweeper and clears the caches.
     */
    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        parties.clear();
        playerToParty.clear();
        invitesByInvitee.clear();
    }

    /**
     * Returns the party a player belongs to, if any.
     *
     * @param uuid player uuid
     * @return party, or {@code null}
     */
    public Party getParty(UUID uuid) {
        Long id = playerToParty.get(uuid);
        return id == null ? null : parties.get(id);
    }

    /**
     * Returns a party by its id.
     *
     * @param partyId party id
     * @return party, or {@code null}
     */
    public Party getPartyById(long partyId) {
        return parties.get(partyId);
    }

    /**
     * Checks whether a player is in a party.
     *
     * @param uuid player uuid
     * @return {@code true} if the player belongs to a party
     */
    public boolean isInParty(UUID uuid) {
        return playerToParty.containsKey(uuid);
    }

    /**
     * Returns the pending invitation for a player, if any.
     *
     * @param uuid player uuid
     * @return invitation, or {@code null}
     */
    public PartyInvite getInvite(UUID uuid) {
        return invitesByInvitee.get(uuid);
    }

    /**
     * Creates a new party with the given player as leader.
     *
     * @param leader the future leader; must not already be in a party
     * @return the new party, or {@code null} if creation failed
     */
    public Party create(Player leader) {
        UUID uuid = leader.getUniqueId();
        if (isInParty(uuid)) return null;

        long id = nextPartyId.getAndIncrement();
        Instant now = Instant.now();
        Party party = new Party(id, uuid, now);

        parties.put(id, party);
        playerToParty.put(uuid, id);

        Bukkit.getPluginManager().callEvent(new PartyCreateEvent(party, leader));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_parties (id, leader_uuid) VALUES (?, ?)")) {
                        ps.setLong(1, id);
                        ps.setString(2, uuid.toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_members (party_id, uuid, joined_at) VALUES (?, ?, ?)")) {
                        ps.setLong(1, id);
                        ps.setString(2, uuid.toString());
                        ps.setTimestamp(3, Timestamp.from(now));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist party creation for %s", uuid);
                }
            });
        }

        return party;
    }

    /**
     * Sends an invitation from a party leader to another player.
     *
     * <p>The invitation replaces any previous pending invitation for
     * the same invitee. If the invitee is already in a party or the
     * inviter is not the party leader, the request fails.</p>
     *
     * @param inviter the party leader
     * @param invitee the invited player
     * @return the created invitation, or {@code null} if rejected
     */
    public PartyInvite invite(Player inviter, Player invitee) {
        Party party = getParty(inviter.getUniqueId());
        if (party == null) return null;
        if (!party.isLeader(inviter.getUniqueId())) return null;
        if (party.size() >= Party.MAX_MEMBERS) return null;
        if (isInParty(invitee.getUniqueId())) return null;

        Instant expiresAt = Instant.now().plus(INVITE_TIMEOUT_SECONDS, ChronoUnit.SECONDS);
        PartyInvite invite = new PartyInvite(
                party.getId(), inviter.getUniqueId(), invitee.getUniqueId(), expiresAt);

        invitesByInvitee.put(invitee.getUniqueId(), invite);

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                        ps.setString(1, invitee.getUniqueId().toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_invites (party_id, inviter, invitee, expires_at) "
                                    + "VALUES (?, ?, ?, ?)")) {
                        ps.setLong(1, party.getId());
                        ps.setString(2, inviter.getUniqueId().toString());
                        ps.setString(3, invitee.getUniqueId().toString());
                        ps.setTimestamp(4, Timestamp.from(expiresAt));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist invite from %s to %s",
                            inviter.getUniqueId(), invitee.getUniqueId());
                }
            });
        }

        return invite;
    }

    /**
     * Accepts the pending invitation of a player.
     *
     * @param invitee the invited player
     * @return the party the player joined, or {@code null} if the
     *         invitation is missing or expired
     */
    public Party acceptInvite(Player invitee) {
        PartyInvite invite = invitesByInvitee.get(invitee.getUniqueId());
        if (invite == null || invite.isExpired()) return null;

        Party party = parties.get(invite.partyId());
        if (party == null) {
            invitesByInvitee.remove(invitee.getUniqueId());
            return null;
        }
        if (isInParty(invitee.getUniqueId())) return null;
        if (party.size() >= Party.MAX_MEMBERS) return null;

        invitesByInvitee.remove(invitee.getUniqueId());

        Instant now = Instant.now();
        party.addMember(invitee.getUniqueId(), now);
        playerToParty.put(invitee.getUniqueId(), party.getId());

        Bukkit.getPluginManager().callEvent(
                new PartyJoinEvent(party, invitee.getUniqueId(), invite.inviter()));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                        ps.setString(1, invitee.getUniqueId().toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_members (party_id, uuid, joined_at) "
                                    + "VALUES (?, ?, ?)")) {
                        ps.setLong(1, party.getId());
                        ps.setString(2, invitee.getUniqueId().toString());
                        ps.setTimestamp(3, Timestamp.from(now));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist party join for %s", invitee.getUniqueId());
                }
            });
        }

        return party;
    }

    /**
     * Denies the pending invitation of a player.
     *
     * @param invitee the invited player
     * @return {@code true} if an invitation was removed
     */
    public boolean denyInvite(Player invitee) {
        PartyInvite removed = invitesByInvitee.remove(invitee.getUniqueId());
        if (removed == null) return false;

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                    ps.setString(1, invitee.getUniqueId().toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to deny invite for %s", invitee.getUniqueId());
                }
            });
        }
        return true;
    }

    /**
     * Kicks a member out of a party.
     *
     * @param leader the party leader
     * @param target uuid of the member to kick
     * @return {@code true} if the member was removed
     */
    public boolean kick(Player leader, UUID target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (!party.isMember(target) || party.isLeader(target)) return false;

        party.removeMember(target);
        playerToParty.remove(target);

        Bukkit.getPluginManager().callEvent(
                new PartyLeaveEvent(party, target, PartyLeaveEvent.Reason.KICKED));

        persistMemberRemoval(party, target);
        return true;
    }

    /**
     * Promotes a member to leader.
     *
     * @param leader the current leader
     * @param target uuid of the member to promote
     * @return {@code true} if the promotion happened
     */
    public boolean promote(Player leader, UUID target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (!party.isMember(target) || party.isLeader(target)) return false;

        party.setLeader(target);
        persistLeader(party);

        return true;
    }

    /**
     * Makes a player leave their party.
     *
     * <p>If the leaving player is the leader and other members remain,
     * the oldest member is promoted. If the player was the last member,
     * the party is disbanded.</p>
     *
     * @param player the leaving player
     * @return the party after the leave, or {@code null} if it was
     *         disbanded
     */
    public Party leave(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) return null;

        UUID promoted = party.removeMember(player.getUniqueId());
        playerToParty.remove(player.getUniqueId());

        if (party.size() == 0) {
            disbandInternal(party, PartyLeaveEvent.Reason.LEFT_LAST);
            return null;
        }

        if (promoted != null) {
            Bukkit.getPluginManager().callEvent(
                    new PartyLeaveEvent(party, player.getUniqueId(),
                            PartyLeaveEvent.Reason.LEFT_PROMOTED));
            persistLeader(party);
        } else {
            Bukkit.getPluginManager().callEvent(
                    new PartyLeaveEvent(party, player.getUniqueId(),
                            PartyLeaveEvent.Reason.LEFT));
        }

        persistMemberRemoval(party, player.getUniqueId());
        return party;
    }

    /**
     * Disbands a party explicitly.
     *
     * @param leader the party leader
     * @return {@code true} if the party was disbanded
     */
    public boolean disband(Player leader) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;

        disbandInternal(party, PartyLeaveEvent.Reason.DISBANDED);
        return true;
    }

    /**
     * Adds a player to a public party.
     *
     * <p>The caller is responsible for checking that the party is
     * public, that it is not full, and that the player is not already
     * in a party. The join is persisted asynchronously.</p>
     *
     * @param player the joining player
     * @param party  the party to join
     * @return {@code true} if the join succeeded
     */
    public boolean joinPublic(Player player, Party party) {
        UUID uuid = player.getUniqueId();
        if (isInParty(uuid)) return false;
        if (party.size() >= Party.MAX_MEMBERS) return false;

        Instant now = Instant.now();
        party.addMember(uuid, now);
        playerToParty.put(uuid, party.getId());

        Bukkit.getPluginManager().callEvent(
                new PartyJoinEvent(party, uuid, party.getLeader()));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO duels_party_members (party_id, uuid, joined_at) "
                                     + "VALUES (?, ?, ?)")) {
                    ps.setLong(1, party.getId());
                    ps.setString(2, uuid.toString());
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist public join for %s", uuid);
                }
            });
        }
        return true;
    }

    /**
     * Returns a snapshot of every active party.
     *
     * @return list of parties
     */
    public List<Party> getAllParties() {
        return new ArrayList<>(parties.values());
    }

    private void disbandInternal(Party party, PartyLeaveEvent.Reason reason) {
        for (UUID member : party.getMembers().keySet()) {
            playerToParty.remove(member);
        }
        parties.remove(party.getId());

        Bukkit.getPluginManager().callEvent(new PartyDisbandEvent(party, reason));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM duels_parties WHERE id = ?")) {
                    ps.setLong(1, party.getId());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to disband party %d", party.getId());
                }
            });
        }
    }

    private void persistMemberRemoval(Party party, UUID uuid) {
        if (!database.isReady()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM duels_party_members WHERE party_id = ? AND uuid = ?")) {
                ps.setLong(1, party.getId());
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to remove member %s from party %d", uuid, party.getId());
            }
        });
    }

    private void persistLeader(Party party) {
        if (!database.isReady()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE duels_parties SET leader_uuid = ? WHERE id = ?")) {
                ps.setString(1, party.getLeader().toString());
                ps.setLong(2, party.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to update leader of party %d", party.getId());
            }
        });
    }

    private void startInviteSweeper() {
        sweepTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::sweepExpiredInvites,
                INVITE_SWEEP_INTERVAL_TICKS, INVITE_SWEEP_INTERVAL_TICKS);
    }

    private void sweepExpiredInvites() {
        List<UUID> expired = new ArrayList<>();
        Instant now = Instant.now();
        for (Map.Entry<UUID, PartyInvite> entry : invitesByInvitee.entrySet()) {
            if (entry.getValue().expiresAt().isBefore(now)) {
                expired.add(entry.getKey());
            }
        }
        if (expired.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : expired) {
                invitesByInvitee.remove(uuid);
            }
        });

        if (!database.isReady()) return;
        try (Connection conn = database.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM duels_party_invites WHERE expires_at < ?")) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.error(e, "Failed to purge expired party invites");
        }
    }
}