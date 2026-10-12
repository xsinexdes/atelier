package dev.atelier.block;

import dev.atelier.AtelierPlugin;
import dev.atelier.item.BlockDef;
import dev.atelier.item.Drops;
import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Custom blocks implemented with note block states (same technique other custom-item plugins use). */
public final class BlockManager implements Listener {
    private final AtelierPlugin plugin;
    private final ItemRegistry reg;

    public BlockManager(AtelierPlugin plugin, ItemRegistry reg) {
        this.plugin = plugin;
        this.reg = reg;
    }

    public ItemDef defAt(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) return null;
        int v = NoteStates.variation((NoteBlock) b.getBlockData());
        return v < 0 ? null : reg.byVariation(v);
    }

    // ── placing ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Block clicked = e.getClickedBlock();

        // Vanilla note blocks: no tuning (their states are reserved for custom blocks)
        if (clicked.getType() == Material.NOTE_BLOCK && plugin.getConfig().getBoolean("blocks.disable-vanilla-noteblock-tuning", true)
                && e.getAction().isRightClick()) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if (!e.getAction().isRightClick() || e.getHand() != EquipmentSlot.HAND) return;

        ItemStack hand = e.getItem();
        ItemDef def = reg.fromStack(hand);
        if (def == null || def.block == null || def.block.variation < 0) return;

        Player p = e.getPlayer();
        if (clicked.getType().isInteractable() && clicked.getType() != Material.NOTE_BLOCK && !p.isSneaking()) return;

        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(e.getBlockFace());
        if (!target.isReplaceable()) return;
        BoundingBox box = BoundingBox.of(target);
        for (var en : target.getWorld().getNearbyEntities(box)) {
            if (en instanceof LivingEntity) { e.setCancelled(true); return; }
        }

        BlockState replaced = target.getState();
        target.setBlockData(NoteStates.data(def.block.variation), false);
        BlockPlaceEvent place = new BlockPlaceEvent(target, replaced, clicked, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(place);
        e.setCancelled(true);
        if (place.isCancelled() || !place.canBuild()) { replaced.update(true, false); return; }

        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), def.block.placeSound, SoundCategory.BLOCKS, 1f, 0.8f);
        p.swingMainHand();
        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemStack held = p.getInventory().getItemInMainHand();
            held.setAmount(held.getAmount() - 1);
            p.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
        }
    }

    // ── breaking ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        ItemDef def = defAt(e.getBlock());
        if (def == null) return;
        e.setDropItems(false);
        e.setExpToDrop(0);
        Block b = e.getBlock();
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), def.block.breakSound, SoundCategory.BLOCKS, 1f, 0.8f);
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        String tool = e.getPlayer().getInventory().getItemInMainHand().getType().getKey().toString();
        List<BlockDef.Drop> drops = null;
        BlockDef.Rule fallback = null;
        for (BlockDef.Rule r : def.block.rules) {
            if (r.tools().isEmpty()) { if (fallback == null) fallback = r; }
            else if (r.tools().contains(tool)) { drops = r.drops(); break; }
        }
        if (drops == null && fallback != null) drops = fallback.drops();
        if (drops == null) drops = def.block.rules.isEmpty() ? List.of(new BlockDef.Drop(def.id, 1.0, 1, 1)) : List.<BlockDef.Drop>of();
        Drops.drop(reg, drops, b.getLocation().add(0.5, 0.5, 0.5));
    }

    // ── keep states stable ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.NOTE_BLOCK) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (defAt(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : new ArrayList<>(e.getBlocks())) if (defAt(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : new ArrayList<>(e.getBlocks())) if (defAt(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> defAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        e.blockList().removeIf(b -> defAt(b) != null);
    }
}
