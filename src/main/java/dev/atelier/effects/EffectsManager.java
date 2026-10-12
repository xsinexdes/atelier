package dev.atelier.effects;

import dev.atelier.AtelierPlugin;
import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** "Effects:" (HIT / USE / HOLD / WEAR) and armor set bonuses ("Armor.bonus"). */
public final class EffectsManager implements Listener {
    private final ItemRegistry reg;

    public EffectsManager(AtelierPlugin plugin, ItemRegistry reg) {
        this.reg = reg;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { for (Player p : Bukkit.getOnlinePlayers()) tick(p); }, 20L, 20L);
    }

    private void tick(Player p) {
        run(p.getInventory().getItemInMainHand(), "HOLD", p, null);
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<Map<?, ?>>> bonus = new HashMap<>();
        for (ItemStack a : p.getInventory().getArmorContents()) {
            run(a, "WEAR", p, null);
            ItemDef d = reg.fromStack(a);
            if (d == null || d.armorSet == null) continue;
            counts.merge(d.armorSet, 1, Integer::sum);
            bonus.computeIfAbsent(d.armorSet, k -> new ArrayList<>()).addAll(d.setBonuses);
        }
        counts.forEach((set, n) -> {
            for (Map<?, ?> b : bonus.get(set)) if (n >= num(b.get("pieces"), 2)) apply(b, p, 60, false);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) return;
        run(p.getInventory().getItemInMainHand(), "HIT", p, victim);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        run(e.getItem(), "EAT", e.getPlayer(), null);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        var target = p.getTargetEntity(6);
        run(e.getItem(), "USE", p, target instanceof LivingEntity le ? le : null);
    }

    private void run(ItemStack stack, String trigger, Player self, LivingEntity enemy) {
        ItemDef d = reg.fromStack(stack);
        if (d == null || d.effects.isEmpty()) return;
        boolean passive = trigger.equals("HOLD") || trigger.equals("WEAR");
        for (Map<?, ?> m : d.effects) {
            if (!trigger.equalsIgnoreCase(String.valueOf(m.get("trigger")))) continue;
            boolean toEnemy = "ENEMY".equalsIgnoreCase(String.valueOf(m.get("target")));
            LivingEntity t = toEnemy ? enemy : self;
            if (t != null) apply(m, t, passive ? 60 : Math.max(1, num(m.get("duration"), 10)) * 20, !passive);
        }
    }

    private void apply(Map<?, ?> m, LivingEntity t, int ticks, boolean particles) {
        PotionEffectType type = PotionEffectType.getByKey(
                NamespacedKey.minecraft(String.valueOf(m.get("effect")).toLowerCase(Locale.ROOT)));
        if (type == null) return;
        int level = Math.max(1, num(m.get("level"), 1)) - 1;
        t.addPotionEffect(new PotionEffect(type, ticks, level, false, particles, true));
    }

    private static int num(Object o, int def) {
        try { return o == null ? def : (int) Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
