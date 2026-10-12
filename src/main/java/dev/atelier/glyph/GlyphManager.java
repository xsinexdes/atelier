package dev.atelier.glyph;

import dev.atelier.AtelierPlugin;
import dev.atelier.content.GlyphDef;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds glyph definitions and replaces :placeholders: in chat. */
public final class GlyphManager implements Listener {
    private final AtelierPlugin plugin;
    private volatile Map<String, GlyphDef> glyphs = new LinkedHashMap<>();

    public GlyphManager(AtelierPlugin plugin) { this.plugin = plugin; }

    public void set(Map<String, GlyphDef> g) { this.glyphs = new LinkedHashMap<>(g); }
    public Collection<GlyphDef> all() { return glyphs.values(); }
    public GlyphDef get(String id) { return glyphs.get(id); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.replace-glyph-placeholders", true)) return;
        Component msg = e.message();
        for (GlyphDef g : glyphs.values()) {
            if (g.codepoint == 0) continue;
            if (g.permission != null && !e.getPlayer().hasPermission(g.permission)) continue;
            for (String ph : g.placeholders) {
                if (ph.isBlank()) continue;
                msg = msg.replaceText(TextReplacementConfig.builder().matchLiteral(ph)
                        .replacement(Component.text(g.asString())).build());
            }
        }
        e.message(msg);
    }
}
