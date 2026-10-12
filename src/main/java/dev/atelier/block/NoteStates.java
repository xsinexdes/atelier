package dev.atelier.block;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.Bukkit;

/**
 * Maps an integer state index (0..799) to a note block state.
 * index = ((instrument * 25) + note) * 2 + powered. Index 0 (harp,0,false) is kept as vanilla default,
 * so a block with custom-variation N uses index N+1.
 */
public final class NoteStates {
    private NoteStates() {}

    /** {Bukkit Instrument name, blockstate json name}. Order defines the index. */
    public static final String[][] INSTRUMENTS = {
            {"PIANO", "harp"}, {"BASS_DRUM", "basedrum"}, {"SNARE_DRUM", "snare"}, {"STICKS", "hat"},
            {"BASS_GUITAR", "bass"}, {"FLUTE", "flute"}, {"BELL", "bell"}, {"GUITAR", "guitar"},
            {"CHIME", "chime"}, {"XYLOPHONE", "xylophone"}, {"IRON_XYLOPHONE", "iron_xylophone"},
            {"COW_BELL", "cow_bell"}, {"DIDGERIDOO", "didgeridoo"}, {"BIT", "bit"}, {"BANJO", "banjo"},
            {"PLING", "pling"}
    };
    /** Extra vanilla instruments (mob heads) - never used for custom blocks, only listed in blockstates. */
    public static final String[] HEAD_INSTRUMENTS =
            {"zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"};

    public static final int MAX_VARIATION = INSTRUMENTS.length * 25 * 2 - 2; // 798

    public static NoteBlock data(int variation) {
        int idx = variation + 1;
        boolean powered = idx % 2 == 1;
        int rest = idx / 2;
        int note = rest % 25;
        int inst = rest / 25;
        NoteBlock nb = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        nb.setInstrument(Instrument.valueOf(INSTRUMENTS[inst][0]));
        nb.setNote(new Note(note));
        nb.setPowered(powered);
        return nb;
    }

    /** @return variation, or -1 when the state is not in the custom range. */
    public static int variation(NoteBlock nb) {
        int inst = -1;
        String name = nb.getInstrument().name();
        for (int i = 0; i < INSTRUMENTS.length; i++) if (INSTRUMENTS[i][0].equals(name)) { inst = i; break; }
        if (inst < 0) return -1;
        int idx = ((inst * 25) + nb.getNote().getId()) * 2 + (nb.isPowered() ? 1 : 0);
        return idx - 1;
    }
}
