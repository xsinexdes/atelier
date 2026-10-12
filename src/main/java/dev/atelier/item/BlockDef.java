package dev.atelier.item;

import java.util.ArrayList;
import java.util.List;

/** Custom block backed by a note block state. */
public final class BlockDef {
    public record Drop(String item, double probability, int min, int max) {}
    /** Empty {@code tools} means "else" (fallback rule). */
    public record Rule(List<String> tools, List<Drop> drops) {}

    /** -1 = auto-assign. Mapped to note block state index variation+1. */
    public int variation = -1;
    public String breakSound = "block.stone.break";
    public String placeSound = "block.stone.place";
    public final List<Rule> rules = new ArrayList<>();
}
