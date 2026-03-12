package com.kawaiimatch;

/**
 * Represents a single game piece on the board.
 */
public class Piece {

    // Type constants
    public static final int EMPTY  = 0;
    public static final int HEART  = 1;  // pink
    public static final int STAR   = 2;  // yellow
    public static final int LEAF   = 3;  // green
    public static final int DROP   = 4;  // blue
    public static final int GEM    = 5;  // purple
    public static final int CANDY  = 6;  // orange

    public int type;

    // Remove animation: 0.0 (fully visible) → 1.0 (gone)
    public boolean removing;
    public float removeAlpha; // 1.0f when alive

    // Fall animation: how many rows this piece fell (set during gravity)
    public int fallRows;

    public Piece(int type) {
        this.type = type;
        this.removing = false;
        this.removeAlpha = 1.0f;
        this.fallRows = 0;
    }
}
