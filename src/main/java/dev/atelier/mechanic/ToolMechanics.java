package dev.atelier.mechanic;

import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Oraxen-compatible tool mechanics:
 *  durability: {value: N}                 custom max durability (the vanilla bar is scaled)
 *  mining: {radius: 1, depth: 1}          break an area (3x3x1 with radius 1)
 *  smelting: {enabled: true}              ores drop smelted
 */
public final class ToolMechanics implements Listener {
    private final ItemRegistry reg;
    private boolean busy;
    private Map<Material, ItemStack> smeltMap;

    public ToolMechanics(ItemRegistry reg) { this.reg = reg; }

    // ───── durability ─────
    @EventHandler(ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        ItemDef d = reg.fromStack(item);
        if (d == null) return;
        ConfigurationSection dur = d.mech("durability");
        if (dur == null || dur.getInt("value", 0) <= 0) return;
        int total = dur.getInt("value");

        ItemMeta meta = item.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(reg.keyDur, PersistentDataType.INTEGER);
        int remaining = (stored == null ? total : stored) - Math.max(1, e.getDamage());
        e.setCancelled(true);                       // we manage the damage ourselves

        Player p = e.getPlayer();
        if (remaining <= 0) {
            p.getWorld().playSound(p.getLocation(), "entity.item.break", 1f, 1f);
            item.setAmount(0);
            return;
        }
        meta.getPersistentDataContainer().set(reg.keyDur, PersistentDataType.INTEGER, remaining);
        int max = item.getType().getMaxDurability();
        if (max > 0 && meta instanceof Damageable dm)
            dm.setDamage((int) Math.min(max - 1, Math.round(max * (1.0 - (double) remaining / total))));
        item.setItemMeta(meta);
    }

    // ───── mining + smelting ─────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (busy) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        ItemDef d = reg.fromStack(tool);
        if (d == null) return;
        ConfigurationSection mine = d.mech("mining");
        ConfigurationSection smelt = d.mech("smelting");
        if (mine == null && smelt == null) return;
        Block origin = e.getBlock();
        if (origin.getType() == Material.NOTE_BLOCK) return;     // custom blocks are handled by BlockManager
        boolean doSmelt = smelt != null && smelt.getBoolean("enabled", true);

        if (doSmelt) {
            e.setDropItems(false);
            for (ItemStack s : origin.getDrops(tool, p)) origin.getWorld().dropItemNaturally(origin.getLocation().add(0.5, 0.5, 0.5), smelted(s));
        }
        if (mine == null) return;

        int radius = Math.max(0, mine.getInt("radius", 1)), depth = Math.max(1, mine.getInt("depth", 1));
        RayTraceResult r = p.rayTraceBlocks(7);
        BlockFace face = r != null && r.getHitBlockFace() != null ? r.getHitBlockFace() : BlockFace.UP;
        int nx = face.getModX(), ny = face.getModY();

        busy = true;
        try {
            for (int dd = 0; dd < depth; dd++)
                for (int a = -radius; a <= radius; a++)
                    for (int b = -radius; b <= radius; b++) {
                        if (dd == 0 && a == 0 && b == 0) continue;
                        int dx, dy, dz;
                        if (nx != 0) { dx = -nx * dd; dy = a; dz = b; }
                        else if (ny != 0) { dx = a; dy = -ny * dd; dz = b; }
                        else { dx = a; dy = b; dz = -face.getModZ() * dd; }
                        Block t = origin.getRelative(dx, dy, dz);
                        if (!canMine(t, tool)) continue;
                        BlockBreakEvent ev = new BlockBreakEvent(t, p);
                        Bukkit.getPluginManager().callEvent(ev);
                        if (ev.isCancelled()) continue;
                        for (ItemStack s : t.getDrops(tool, p))
                            t.getWorld().dropItemNaturally(t.getLocation().add(0.5, 0.5, 0.5), doSmelt ? smelted(s) : s);
                        t.getWorld().playEffect(t.getLocation(), Effect.STEP_SOUND, t.getBlockData());
                        t.setType(Material.AIR);
                    }
        } finally {
            busy = false;
        }
    }

    private static boolean canMine(Block t, ItemStack tool) {
        Material m = t.getType();
        return !m.isAir() && !t.isLiquid() && m != Material.NOTE_BLOCK && m.getHardness() >= 0 && t.isPreferredTool(tool);
    }

    private ItemStack smelted(ItemStack s) {
        String n = s.getType().name();
        if (!(n.endsWith("_ORE") || n.startsWith("RAW_") || n.equals("ANCIENT_DEBRIS"))) return s;
        if (smeltMap == null) {
            smeltMap = new HashMap<>();
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                if (it.next() instanceof FurnaceRecipe fr && fr.getInputChoice() instanceof RecipeChoice.MaterialChoice mc)
                    for (Material m : mc.getChoices()) smeltMap.putIfAbsent(m, fr.getResult());
            }
        }
        ItemStack r = smeltMap.get(s.getType());
        if (r == null) return s;
        ItemStack out = r.clone();
        out.setAmount(s.getAmount() * r.getAmount());
        return out;
    }
}
