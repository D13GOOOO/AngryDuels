package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.duel.DuelPhase;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Applies the combat rules that protect duel integrity.
 *
 * <p>Four protections are enforced:</p>
 * <ul>
 *     <li><b>Countdown immunity</b>: no damage is dealt during the
 *     pre-fight phase, regardless of source.</li>
 *     <li><b>Friendly fire blocking</b>: members of the same team
 *     cannot damage each other during the active phase.</li>
 *     <li><b>Duel world isolation</b>: inside the duel world, PvP is
 *     only allowed between the two opposing teams of the same active
 *     session.</li>
 *     <li><b>Countdown freeze</b>: players cannot move on the X, Y or
 *     Z axes during the countdown, but can still look around.</li>
 * </ul>
 */
public class DuelProtectionListener implements Listener {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new protection listener.
     *
     * @param plugin owning plugin
     */
    public DuelProtectionListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels all damage dealt to a duelist during the countdown phase.
     *
     * @param event the incoming damage event
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        DuelSession session = plugin.duels().getSession(victim.getUniqueId());
        if (session == null) return;
        if (session.getPhase() == DuelPhase.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Restricts PvP to opposing teams of the same active session, and
     * blocks friendly fire between teammates.
     *
     * <p>Self-inflicted damage from a projectile is ignored, so the
     * handler only enforces rules between distinct players. When the
     * victim and the attacker do not share a session, damage is
     * cancelled only if either of them is inside the duel world, which
     * prevents cross-session interference without affecting PvP in the
     * rest of the server.</p>
     *
     * @param event the damage-by-entity event
     */
    @EventHandler(ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        Player victim = resolve(event.getEntity());
        Player attacker = resolve(event.getDamager());
        if (victim == null || attacker == null) return;
        if (victim.getUniqueId().equals(attacker.getUniqueId())) return;

        DuelSession victimSession = plugin.duels().getSession(victim.getUniqueId());
        DuelSession attackerSession = plugin.duels().getSession(attacker.getUniqueId());

        if (victimSession != null && victimSession == attackerSession) {
            if (victimSession.getPhase() != DuelPhase.ACTIVE) {
                event.setCancelled(true);
                return;
            }
            if (victimSession.teamOf(victim.getUniqueId())
                    == victimSession.teamOf(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.worlds().isDuelWorld(victim.getWorld())
                || plugin.worlds().isDuelWorld(attacker.getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Freezes duelists on their spawn during the countdown phase while
     * still allowing them to look around.
     *
     * <p>When the target location differs from the source on any
     * cartesian axis, the event destination is replaced with a
     * location that keeps the source X, Y and Z but preserves the
     * target yaw and pitch, so head rotation is not affected by the
     * freeze.</p>
     *
     * @param event the move event
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        DuelSession session = plugin.duels().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.getPhase() != DuelPhase.COUNTDOWN) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()) {
            event.setTo(new Location(
                    to.getWorld(),
                    from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    /**
     * Resolves the player behind an entity, unwrapping the shooter of
     * a projectile.
     *
     * @param entity entity to resolve
     * @return the player, or {@code null} if the entity is not
     *         player-controlled
     */
    private Player resolve(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}