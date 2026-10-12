package dev.atelier.content;

import java.util.ArrayList;
import java.util.List;

public final class GlyphDef {
    public final String id;
    public String texture;          // "ns:path"
    public int ascent = 8, height = 8;
    public Character explicitChar;
    public int codepoint;           // assigned
    public final List<String> placeholders = new ArrayList<>();
    public String permission;

    public GlyphDef(String id) { this.id = id; }
    public String asString() { return String.valueOf((char) codepoint); }
}
