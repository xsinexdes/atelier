package dev.atelier.item;

import java.util.ArrayList;
import java.util.List;

public final class FurnitureDef {
    public float width = 1f, height = 1f;
    public String transform = "FIXED";
    public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;
    public boolean solid;           // places a barrier block
    public boolean fixedRotation;
    public boolean floor = true, wall = false, roof = false;
    public final List<BlockDef.Drop> drops = new ArrayList<>();
}
