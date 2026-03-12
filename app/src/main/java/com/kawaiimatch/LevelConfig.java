package com.kawaiimatch;

/**
 * Defines configuration for each of the 20 levels.
 *
 * pieceTypes: how many different piece types appear (3–6).
 *   Fewer types = easier to match.
 * targetScore: score needed to clear the level.
 * maxMoves: player's move allowance for this level.
 */
public class LevelConfig {

    public final int level;
    public final int targetScore;
    public final int maxMoves;
    public final int pieceTypes;

    public LevelConfig(int level, int targetScore, int maxMoves, int pieceTypes) {
        this.level       = level;
        this.targetScore = targetScore;
        this.maxMoves    = maxMoves;
        this.pieceTypes  = pieceTypes;
    }

    // -----------------------------------------------------------------------
    // 20 levels, difficulty rising steadily.
    // -----------------------------------------------------------------------
    public static final LevelConfig[] ALL = {
        // ── Stage 1: Tutorial  (3 types, generous moves) ──────────────────
        new LevelConfig( 1,  300, 22, 3),
        new LevelConfig( 2,  450, 22, 3),
        new LevelConfig( 3,  600, 20, 3),

        // ── Stage 2: Easy  (4 types) ──────────────────────────────────────
        new LevelConfig( 4,  800, 20, 4),
        new LevelConfig( 5, 1000, 18, 4),
        new LevelConfig( 6, 1300, 18, 4),
        new LevelConfig( 7, 1600, 17, 4),

        // ── Stage 3: Medium  (5 types) ────────────────────────────────────
        new LevelConfig( 8, 2000, 16, 5),
        new LevelConfig( 9, 2400, 16, 5),
        new LevelConfig(10, 2900, 15, 5),
        new LevelConfig(11, 3400, 15, 5),
        new LevelConfig(12, 4000, 14, 5),

        // ── Stage 4: Hard  (6 types) ──────────────────────────────────────
        new LevelConfig(13, 4600, 14, 6),
        new LevelConfig(14, 5300, 13, 6),
        new LevelConfig(15, 6100, 13, 6),
        new LevelConfig(16, 7000, 12, 6),
        new LevelConfig(17, 8000, 12, 6),

        // ── Stage 5: Expert  (6 types, fewest moves) ──────────────────────
        new LevelConfig(18, 9000, 11, 6),
        new LevelConfig(19,10200, 10, 6),
        new LevelConfig(20,12000,  9, 6),
    };

    public static final int TOTAL = ALL.length; // 20
}
