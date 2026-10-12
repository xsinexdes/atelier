package dev.atelier.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    /** Accepts MiniMessage (Oraxen style) or legacy &/§ codes (ItemsAdder style). */
    public static Component parse(String s) {
        if (s == null) return Component.empty();
        Component c;
        if ((s.indexOf('&') >= 0 || s.indexOf('§') >= 0) && s.indexOf('<') < 0) {
            c = LEGACY.deserialize(s.replace('§', '&'));
        } else {
            c = MM.deserialize(s);
        }
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** "default/x.png" + "minecraft" -> "minecraft:default/x". Already-qualified refs are kept. */
    public static String qualify(String ref, String defaultNs) {
        if (ref == null) return null;
        String r = ref.trim().replace('\\', '/');
        if (r.endsWith(".png") || r.endsWith(".json")) r = r.substring(0, r.lastIndexOf('.'));
        return r.contains(":") ? r : defaultNs + ":" + r;
    }

    public static String ns(String q) { return q.substring(0, q.indexOf(':')); }
    public static String path(String q) { return q.substring(q.indexOf(':') + 1); }
}
