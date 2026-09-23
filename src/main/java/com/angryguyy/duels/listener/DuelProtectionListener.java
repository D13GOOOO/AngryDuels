package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.duel.DuelPhase;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Applies the combat rules that protect duel integrity.
 *
 * <p>Two distinct protections are enforced:</p>
 * <ul>
 *     <li><b>Countdown immunity</b> — while a duel is in the
 *     {@link DuelPhase#COUNTDOWN} phase, any damage directed at a
 *     duelist is cancelled regardless of source. This gives both
 *     players time to prepare and prevents accidental early kills.</li>
 *     <li><b>Duel world isolation</b> — inside the dedicated duel
 *     world, PvP is only allowed when both the attacker and the victim
 *     belong to the same session and that session is in the
 *     {@link DuelPhase#ACTIVE} phase. Any other combination is
 *     cancelled, so spectators, waiting players, or a duelist attacking
 *     a non-duelist cannot cause damage.</li>
 * </ul>
 *
 * <p>Projectiles are resolved back to their shooter so that bow,
 * crossbow and trident hits are covered by the same rules.</p>
 */
public class DuelProtectionListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new protection listener.
     *
     * @param plugin owning plugin, used to look up sessions and worlds
     */
    public DuelProtectionListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels all damage dealt to a duelist during the countdown phase.
     *
     * <p>Only players who are currently part of a session are affected.
     * Non-duelists, including players standing in the duel world
     * outside a match, are not touched by this handler.</p>
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
     * Restricts PvP inside the duel world to legitimately active duels.
     *
     * <p>The handler is a no-op if either side of the event cannot be
     * resolved to a player, which naturally excludes environmental
     * damage and mob attacks. Inside the duel world, damage is only
     * allowed if both players share the exact same active session;
     * every other scenario is cancelled.</p>
     *
     * @param event the damage-by-entity event
     */
    @EventHandler(ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        Player victim = resolve(event.getEntity());
        Player attacker = resolve(event.getDamager());
        if (victim == null || attacker == null) return;

        if (plugin.worlds().isDuelWorld(victim.getWorld())) {
            DuelSession vSession = plugin.duels().getSession(victim.getUniqueId());
            if (vSession == null || vSession.getPhase() != DuelPhase.ACTIVE) {
                event.setCancelled(true);
                return;
            }
            DuelSession aSession = plugin.duels().getSession(attacker.getUniqueId());
            if (aSession != vSession) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Resolves an entity to a player, following projectiles back to
     * their shooter.
     *
     * @param entity the entity to resolve
     * @return the resolved player, or {@code null} if the entity is
     *         neither a player nor a projectile fired by one
     */
    private Player resolve(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /**
     * Freezes duelists in place during the countdown phase.
     *
     * <p>Any movement attempt is cancelled before the server applies it,
     * so the player stays exactly on the spawn point assigned by the
     * arena. Movement is released the instant the session switches to
     * {@link DuelPhase#ACTIVE}.</p>
     *
     * <p>Only horizontal and vertical displacement is blocked; head
     * rotation is left untouched so players can still look around while
     * waiting for the fight to start.</p>
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
            event.setTo(from);
        }
    }
}