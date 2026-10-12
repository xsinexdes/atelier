package dev.atelier.furniture;

import dev.atelier.AtelierPlugin;
import dev.atelier.item.Drops;
import dev.atelier.item.FurnitureDef;
import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/** Furniture = ItemDisplay (visual) + Interaction (hitbox), linked through PDC. */
public final class FurnitureManager implements Listener {
    private final AtelierPlugin plugin;
    private final ItemRegistry reg;
    private final NamespacedKey kId, kLink, kBarrier;

    public FurnitureManager(AtelierPlugin plugin, ItemRegistry reg) {
        this.plugin = plugin;
        this.reg = reg;
        kId = new NamespacedKey(plugin, "furniture");
        kLink = new NamespacedKey(plugin, "furniture_link");
        kBarrier = new NamespacedKey(plugin, "furniture_barrier");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        ItemStack hand = e.getItem();
        ItemDef def = reg.fromStack(hand);
        if (def == null || def.furniture == null) return;
        FurnitureDef f = def.furniture;
        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();

        if (clicked.getType().isInteractable() && !p.isSneaking()) return;
        boolean ok = switch (face) {
            case UP -> f.floor;
            case DOWN -> f.roof;
            case NORTH, SOUTH, EAST, WEST -> f.wall;
            default -> false;
        };
        e.setCancelled(true);
        if (!ok) return;

        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable()) return;

        BlockState replaced = target.getState();
        BlockPlaceEvent place = new BlockPlaceEvent(target, replaced, clicked, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) return;

        float yaw = f.fixedRotation ? 0f : Math.round((p.getLocation().getYaw() + 180f) / 45f) * 45f;
        spawn(def, target, yaw);
        if (f.solid) target.setType(Material.BARRIER, false);
        p.swingMainHand();
        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemStack held = p.getInventory().getItemInMainHand();
            held.setAmount(held.getAmount() - 1);
            p.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
        }
    }

    private void spawn(ItemDef def, Block target, float yaw) {
        FurnitureDef f = def.furniture;
        World w = target.getWorld();
        ItemStack model = reg.create(def, 1);
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        Location base = target.getLocation().add(0.5, 0.0, 0.5);

        ItemDisplay display = w.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(model);
            try { d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(f.transform.toUpperCase())); }
            catch (IllegalArgumentException ex) { d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); }
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(f.scaleX, f.scaleY, f.scaleZ), new AxisAngle4f()));
            d.setRotation(yaw, 0f);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(kId, PersistentDataType.STRING, def.id);
        });
        w.spawn(base, Interaction.class, i -> {
            i.setInteractionWidth(f.width);
            i.setInteractionHeight(f.height);
            i.setResponsive(true);
            i.setPersistent(true);
            var pdc = i.getPersistentDataContainer();
            pdc.set(kId, PersistentDataType.STRING, def.id);
            pdc.set(kLink, PersistentDataType.STRING, display.getUniqueId().toString());
            if (f.solid) pdc.set(kBarrier, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Left click on the hitbox removes the furniture and drops its items. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Entity hit = e.getAttacked();
        if (!(hit instanceof Interaction i)) return;
        String id = i.getPersistentDataContainer().get(kId, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        Block base = i.getLocation().getBlock();
        BlockBreakEvent test = new BlockBreakEvent(base, p);
        test.setDropItems(false);
        plugin.getServer().getPluginManager().callEvent(test);
        if (test.isCancelled()) return;

        ItemDef def = reg.get(id);
        remove(i);
        if (def != null && p.getGameMode() != GameMode.CREATIVE) {
            Location at = i.getLocation().add(0, 0.5, 0);
            if (def.furniture != null && !def.furniture.drops.isEmpty()) Drops.drop(reg, def.furniture.drops, at);
            else at.getWorld().dropItemNaturally(at, reg.create(def, 1));
        }
    }

    private void remove(Interaction i) {
        var pdc = i.getPersistentDataContainer();
        String link = pdc.get(kLink, PersistentDataType.STRING);
        if (link != null) {
            try {
                Entity d = Bukkit.getEntity(UUID.fromString(link));
                if (d != null) d.remove();
            } catch (IllegalArgumentException ignored) {}
        }
        if (pdc.has(kBarrier, PersistentDataType.BYTE)) {
            Block b = i.getLocation().getBlock();
            if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
        }
        i.remove();
    }
}
