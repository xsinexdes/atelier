package dev.atelier.mechanic;

import dev.atelier.AtelierPlugin;
import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * lifeleech: {amount}                                   heal yourself when hitting
 * bleeding: {chance, duration, interval, damage_per_interval}   damage over time (ticks)
 * thor: {lightning_bolts_amount, random_location_variation, delay(ms)}   right click strikes lightning
 */
public final class CombatMechanics implements Listener {
    private final AtelierPlugin plugin;
    private final ItemRegistry reg;
    private final Cooldowns cd = new Cooldowns();
    private final Set<UUID> bleeding = new HashSet<>();

    public CombatMechanics(AtelierPlugin plugin, ItemRegistry reg) {
        this.plugin = plugin;
        this.reg = reg;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) return;
        ItemDef d = reg.fromStack(p.getInventory().getItemInMainHand());
        if (d == null) return;

        ConfigurationSection ll = d.mech("lifeleech");
        if (ll != null) {
            AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + ll.getDouble("amount", 1)));
        }
        ConfigurationSection bl = d.mech("bleeding");
        if (bl != null && !bleeding.contains(victim.getUniqueId())) {
            double chance = bl.getDouble("chance", 1.0);
            if (chance > 1) chance /= 100.0;
            if (ThreadLocalRandom.current().nextDouble() <= chance)
                bleed(victim, bl.getInt("duration", 100), Math.max(1, bl.getInt("interval", 20)), bl.getDouble("damage_per_interval", 1));
        }
    }

    private void bleed(LivingEntity v, int duration, int interval, double dmg) {
        bleeding.add(v.getUniqueId());
        new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                elapsed += interval;
                if (!v.isValid() || v.isDead() || elapsed > duration) { bleeding.remove(v.getUniqueId()); cancel(); return; }
                v.damage(dmg);
                v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, 1, 0), 4);
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        ItemDef d = reg.fromStack(e.getItem());
        if (d == null) return;
        ConfigurationSection t = d.mech("thor");
        if (t == null) return;
        Player p = e.getPlayer();
        if (!cd.ready(p.getUniqueId() + ":" + d.id + ":thor", t.getLong("delay", 5000))) return;

        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTrace(eye, eye.getDirection(), 40, FluidCollisionMode.NEVER, true, 0.2, en -> en != p);
        Location target = r == null ? eye.clone().add(eye.getDirection().multiply(20)) : r.getHitPosition().toLocation(p.getWorld());
        int n = Math.max(1, t.getInt("lightning_bolts_amount", 1));
        double var = t.getDouble("random_location_variation", 0);
        for (int i = 0; i < n; i++) {
            double ox = var <= 0 ? 0 : ThreadLocalRandom.current().nextDouble(-var, var);
            double oz = var <= 0 ? 0 : ThreadLocalRandom.current().nextDouble(-var, var);
            p.getWorld().strikeLightning(target.clone().add(ox, 0, oz));
        }
    }
}
