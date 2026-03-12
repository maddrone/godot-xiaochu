package com.kawaiimatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core game logic – optimised version.
 *
 * New features vs. v1:
 *  • Combo multiplier  – chain reactions multiply the score (×1.5 / ×2.0 / ×2.5)
 *  • Invalid-swap flash – red flash shown on the two cells that failed to match
 *  • Deadlock detection – after every move checks for any valid swap remaining
 *  • Auto-shuffle       – if deadlocked the board is shuffled and a message shown
 *  • High-score table   – best score per level is remembered in-session
 */
public class GameEngine {

    // ── Board ─────────────────────────────────────────────────────────────
    public static final int COLS = 8;
    public static final int ROWS = 6;

    // ── States ────────────────────────────────────────────────────────────
    public static final int STATE_TITLE     = 0;
    public static final int STATE_PLAYING   = 1;
    public static final int STATE_ANIMATING = 2;
    public static final int STATE_WIN       = 3;
    public static final int STATE_GAME_OVER = 4;
    public static final int STATE_PAUSED    = 5;
    public static final int STATE_ALL_CLEAR = 6;

    // ── Animation phases ──────────────────────────────────────────────────
    public static final int ANIM_REMOVE = 0;
    public static final int ANIM_FALL   = 1;

    // ── Tick durations ────────────────────────────────────────────────────
    public static final int REMOVE_TICKS      = 10;
    public static final int FALL_TICKS        = 12;
    public static final int POPUP_TICKS       = 45;
    public static final int INVALID_TICKS     = 22;  // invalid-swap red flash
    public static final int COMBO_TICKS       = 58;  // combo overlay display
    public static final int SHUFFLE_MSG_TICKS = 85;  // shuffle notification

    // ── Public state (read by renderer) ──────────────────────────────────
    public int     state;
    public int     animPhase;
    public int     animTick;
    public boolean isAnimating;

    public int cursorX, cursorY;
    public int selectedX = -1, selectedY = -1;

    public int score;
    public int moves;
    public int currentLevel;
    public LevelConfig levelCfg;

    // Score popup
    public int popupScore;
    public int popupCol, popupRow;
    public int popupTick;

    // Combo chain
    public int comboCount;        // depth of current chain (1 = first match)
    public int comboDisplayTick;  // frames remaining for combo overlay
    public int comboDisplayCount; // the N shown in "COMBO ×N"

    // Invalid-swap flash
    public int invalidCol1 = -1, invalidRow1 = -1;
    public int invalidCol2 = -1, invalidRow2 = -1;
    public int invalidTick = 0;

    // Shuffle notification
    public boolean showShuffleMsg = false;
    public int     shuffleMsgTick = 0;

    // High scores (per level, kept for the session)
    public int[] highScores;

    // ── Private ───────────────────────────────────────────────────────────
    private Piece[][] grid;
    private Random    rng;

    // ════════════════════════════════════════════════════════════════════════
    //  Constructor
    // ════════════════════════════════════════════════════════════════════════

    public GameEngine() {
        rng        = new Random();
        grid       = new Piece[COLS][ROWS];
        highScores = new int[LevelConfig.TOTAL];
        state      = STATE_TITLE;
        currentLevel = 0;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Public accessors
    // ════════════════════════════════════════════════════════════════════════

    public Piece getPiece(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return null;
        return grid[col][row];
    }

    public boolean isLastLevel() {
        return currentLevel >= LevelConfig.TOTAL - 1;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Input handlers
    // ════════════════════════════════════════════════════════════════════════

    public void onDpad(int dx, int dy) {
        if (state != STATE_PLAYING || isAnimating) return;
        if (selectedX >= 0) {
            // A piece is selected → try to swap in this direction
            int tx = selectedX + dx;
            int ty = selectedY + dy;
            trySwap(selectedX, selectedY, tx, ty);
            cursorX   = clampCol(selectedX + dx);
            cursorY   = clampRow(selectedY + dy);
            selectedX = -1;
            selectedY = -1;
        } else {
            cursorX = clampCol(cursorX + dx);
            cursorY = clampRow(cursorY + dy);
        }
    }

    public void onButtonA() {
        switch (state) {
            case STATE_TITLE:
                startLevel(0);
                break;

            case STATE_PLAYING:
                if (isAnimating) return;
                if (selectedX < 0) {
                    selectedX = cursorX;
                    selectedY = cursorY;
                } else if (selectedX == cursorX && selectedY == cursorY) {
                    selectedX = -1;
                    selectedY = -1;
                } else {
                    trySwap(selectedX, selectedY, cursorX, cursorY);
                    selectedX = -1;
                    selectedY = -1;
                }
                break;

            case STATE_WIN:
                if (isLastLevel()) state = STATE_ALL_CLEAR;
                else               startLevel(currentLevel + 1);
                break;

            case STATE_GAME_OVER:
                startLevel(currentLevel);
                break;

            case STATE_ALL_CLEAR:
                state = STATE_TITLE;
                break;
        }
    }

    public void onButtonB() {
        if (state == STATE_PLAYING) {
            selectedX = -1;
            selectedY = -1;
        }
    }

    public void onButtonStart() {
        if      (state == STATE_PLAYING) state = STATE_PAUSED;
        else if (state == STATE_PAUSED)  state = STATE_PLAYING;
        else if (state == STATE_TITLE)   startLevel(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Game loop update
    // ════════════════════════════════════════════════════════════════════════

    public void update() {
        if (popupTick       > 0) popupTick--;
        if (invalidTick     > 0) invalidTick--;
        if (comboDisplayTick > 0) comboDisplayTick--;
        if (shuffleMsgTick  > 0) {
            shuffleMsgTick--;
            if (shuffleMsgTick == 0) showShuffleMsg = false;
        }

        if (!isAnimating) return;
        animTick++;
        if (animPhase == ANIM_REMOVE) updateRemoveAnim();
        else                          updateFallAnim();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Animation steps
    // ════════════════════════════════════════════════════════════════════════

    private void updateRemoveAnim() {
        float progress = (float) animTick / REMOVE_TICKS;
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++)
                if (grid[c][r] != null && grid[c][r].removing)
                    grid[c][r].removeAlpha = 1.0f - progress;

        if (animTick >= REMOVE_TICKS) {
            // Remove matched pieces and calculate score
            int removed = 0, sumC = 0, sumR = 0;
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (grid[c][r] != null && grid[c][r].removing) {
                        sumC += c; sumR += r; removed++;
                        grid[c][r] = null;
                    }
                }
            }

            // Score = base + size bonus, then apply combo multiplier
            int   base  = removed * 10 + Math.max(0, removed - 3) * 5;
            float multi = comboCount >= 4 ? 2.5f
                        : comboCount == 3 ? 2.0f
                        : comboCount == 2 ? 1.5f : 1.0f;
            int gained = (int)(base * multi);
            score     += gained;
            popupScore = gained;
            popupCol   = sumC / removed;
            popupRow   = sumR / removed;
            popupTick  = POPUP_TICKS;

            // Show combo overlay when chaining
            if (comboCount >= 2) {
                comboDisplayCount = comboCount;
                comboDisplayTick  = COMBO_TICKS;
            }

            applyGravity();
            animPhase = ANIM_FALL;
            animTick  = 0;
        }
    }

    private void updateFallAnim() {
        if (animTick >= FALL_TICKS) {
            // Settle all pieces
            for (int c = 0; c < COLS; c++)
                for (int r = 0; r < ROWS; r++)
                    if (grid[c][r] != null) grid[c][r].fallRows = 0;

            List<int[]> chains = findMatches();
            if (!chains.isEmpty()) {
                comboCount++;           // deepen the combo chain
                markRemoving(chains);
                animPhase = ANIM_REMOVE;
                animTick  = 0;
            } else {
                isAnimating = false;
                comboCount  = 0;
                checkWinLose();
                if (state == STATE_PLAYING) checkDeadlock();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Level management
    // ════════════════════════════════════════════════════════════════════════

    private void startLevel(int index) {
        currentLevel     = index;
        levelCfg         = LevelConfig.ALL[index];
        score            = 0;
        moves            = levelCfg.maxMoves;
        cursorX          = COLS / 2;
        cursorY          = ROWS / 2;
        selectedX        = -1; selectedY = -1;
        isAnimating      = false;
        animPhase        = ANIM_REMOVE;
        animTick         = 0;
        popupTick        = 0;
        comboCount       = 0;
        comboDisplayTick = 0;
        invalidTick      = 0;
        showShuffleMsg   = false;
        shuffleMsgTick   = 0;
        fillGrid();
        state = STATE_PLAYING;
    }

    private void fillGrid() {
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++)
                grid[c][r] = new Piece(randomType());
        resolveInitialMatches();
        // Guarantee at least one valid move on the initial board
        int guard = 15;
        while (!hasValidMoves() && guard-- > 0) doShuffle(false);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Match logic
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Replace any piece that is part of a 3-in-a-row with a non-matching type.
     */
    private void resolveInitialMatches() {
        boolean changed = true;
        int guard = 300;
        while (changed && guard-- > 0) {
            changed = false;
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (isPartOfMatch(c, r)) {
                        int old = grid[c][r].type;
                        for (int t = 1; t <= levelCfg.pieceTypes; t++) {
                            if (t != old) {
                                grid[c][r].type = t;
                                if (!isPartOfMatch(c, r)) break;
                            }
                        }
                        changed = true;
                    }
                }
            }
        }
    }

    private boolean isPartOfMatch(int col, int row) {
        int type = grid[col][row].type;
        int h = 1;
        for (int c = col - 1; c >= 0    && grid[c][row].type == type; c--) h++;
        for (int c = col + 1; c < COLS  && grid[c][row].type == type; c++) h++;
        if (h >= 3) return true;
        int v = 1;
        for (int r = row - 1; r >= 0    && grid[col][r].type == type; r--) v++;
        for (int r = row + 1; r < ROWS  && grid[col][r].type == type; r++) v++;
        return v >= 3;
    }

    private void trySwap(int c1, int r1, int c2, int r2) {
        if (c2 < 0 || c2 >= COLS || r2 < 0 || r2 >= ROWS) return;
        if (Math.abs(c1 - c2) + Math.abs(r1 - r2) != 1) return;

        rawSwap(c1, r1, c2, r2);
        List<int[]> matches = findMatches();

        if (matches.isEmpty()) {
            rawSwap(c1, r1, c2, r2);           // swap back – no match
            invalidCol1 = c1; invalidRow1 = r1;
            invalidCol2 = c2; invalidRow2 = r2;
            invalidTick = INVALID_TICKS;
        } else {
            moves--;
            comboCount = 1;
            markRemoving(matches);
            isAnimating = true;
            animPhase   = ANIM_REMOVE;
            animTick    = 0;
        }
    }

    private void rawSwap(int c1, int r1, int c2, int r2) {
        Piece tmp = grid[c1][r1];
        grid[c1][r1] = grid[c2][r2];
        grid[c2][r2] = tmp;
    }

    private List<int[]> findMatches() {
        boolean[][] matched = new boolean[COLS][ROWS];
        // Horizontal
        for (int r = 0; r < ROWS; r++) {
            int c = 0;
            while (c < COLS) {
                int type = grid[c][r].type, len = 1;
                while (c + len < COLS && grid[c + len][r].type == type) len++;
                if (len >= 3) for (int k = 0; k < len; k++) matched[c + k][r] = true;
                c += len;
            }
        }
        // Vertical
        for (int c = 0; c < COLS; c++) {
            int r = 0;
            while (r < ROWS) {
                int type = grid[c][r].type, len = 1;
                while (r + len < ROWS && grid[c][r + len].type == type) len++;
                if (len >= 3) for (int k = 0; k < len; k++) matched[c][r + k] = true;
                r += len;
            }
        }
        List<int[]> result = new ArrayList<int[]>();
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++)
                if (matched[c][r]) result.add(new int[]{c, r});
        return result;
    }

    private void markRemoving(List<int[]> cells) {
        for (int[] pos : cells) {
            Piece p = grid[pos[0]][pos[1]];
            if (p != null) { p.removing = true; p.removeAlpha = 1.0f; }
        }
    }

    /**
     * Pull pieces down to fill gaps; top cells receive fresh random pieces.
     * Sets piece.fallRows so the renderer can animate the drop.
     */
    private void applyGravity() {
        for (int c = 0; c < COLS; c++) {
            int write = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--) {
                Piece p = grid[c][r];
                if (p != null) {
                    p.fallRows    = write - r;
                    p.removing    = false;
                    p.removeAlpha = 1.0f;
                    grid[c][write] = p;
                    if (write != r) grid[c][r] = null;
                    write--;
                }
            }
            for (int r = write; r >= 0; r--) {
                grid[c][r] = new Piece(randomType());
            }
        }
    }

    private void checkWinLose() {
        if (score > highScores[currentLevel])
            highScores[currentLevel] = score;

        if (score >= levelCfg.targetScore)
            state = isLastLevel() ? STATE_ALL_CLEAR : STATE_WIN;
        else if (moves <= 0)
            state = STATE_GAME_OVER;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Deadlock detection + auto-shuffle
    // ════════════════════════════════════════════════════════════════════════

    private void checkDeadlock() {
        if (!hasValidMoves()) doShuffle(true);
    }

    /**
     * Returns true if at least one adjacent swap would produce a match.
     * Uses temporary in-place swaps to avoid allocations.
     */
    public boolean hasValidMoves() {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (c + 1 < COLS) {
                    rawSwap(c, r, c + 1, r);
                    boolean ok = !findMatches().isEmpty();
                    rawSwap(c, r, c + 1, r);
                    if (ok) return true;
                }
                if (r + 1 < ROWS) {
                    rawSwap(c, r, c, r + 1);
                    boolean ok = !findMatches().isEmpty();
                    rawSwap(c, r, c, r + 1);
                    if (ok) return true;
                }
            }
        }
        return false;
    }

    /**
     * Fisher-Yates shuffle of piece types, then resolves initial matches.
     * Retries until a valid-move state is found (up to 20 attempts).
     */
    private void doShuffle(boolean showMsg) {
        int guard = 20;
        do {
            List<Integer> types = new ArrayList<Integer>();
            for (int c = 0; c < COLS; c++)
                for (int r = 0; r < ROWS; r++)
                    types.add(grid[c][r].type);

            for (int i = types.size() - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = types.get(i);
                types.set(i, types.get(j));
                types.set(j, tmp);
            }

            int idx = 0;
            for (int c = 0; c < COLS; c++)
                for (int r = 0; r < ROWS; r++)
                    grid[c][r].type = types.get(idx++);

            resolveInitialMatches();
        } while (!hasValidMoves() && --guard > 0);

        if (showMsg) {
            showShuffleMsg = true;
            shuffleMsgTick = SHUFFLE_MSG_TICKS;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════════

    private int randomType() { return rng.nextInt(levelCfg.pieceTypes) + 1; }
    private int clampCol(int c) { return Math.max(0, Math.min(COLS - 1, c)); }
    private int clampRow(int r) { return Math.max(0, Math.min(ROWS - 1, r)); }
}
