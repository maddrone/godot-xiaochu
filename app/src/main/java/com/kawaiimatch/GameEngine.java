package com.kawaiimatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core game logic for Kawaii Match.
 *
 * Grid layout  : COLS × ROWS  (col 0 = left, row 0 = top)
 * Piece storage: grid[col][row]
 *
 * State machine
 * ─────────────
 *   STATE_TITLE      → press Start  → STATE_PLAYING
 *   STATE_PLAYING    → valid swap   → STATE_ANIMATING
 *   STATE_PLAYING    → pause        → STATE_PAUSED
 *   STATE_ANIMATING  → done, target → STATE_WIN
 *   STATE_ANIMATING  → done, no mov → STATE_GAME_OVER
 *   STATE_ANIMATING  → done, ok     → STATE_PLAYING
 *   STATE_WIN        → press A      → next level / STATE_ALL_CLEAR
 *   STATE_GAME_OVER  → press A      → retry same level
 *   STATE_ALL_CLEAR  → press A      → STATE_TITLE
 *   STATE_PAUSED     → press Start  → STATE_PLAYING
 */
public class GameEngine {

    // ── Board dimensions ──────────────────────────────────────────────────
    public static final int COLS = 8;
    public static final int ROWS = 6;

    // ── Game states ───────────────────────────────────────────────────────
    public static final int STATE_TITLE     = 0;
    public static final int STATE_PLAYING   = 1;
    public static final int STATE_ANIMATING = 2;
    public static final int STATE_WIN       = 3;
    public static final int STATE_GAME_OVER = 4;
    public static final int STATE_PAUSED    = 5;
    public static final int STATE_ALL_CLEAR = 6;

    // ── Animation phases (used while state == STATE_ANIMATING) ────────────
    public static final int ANIM_REMOVE = 0;  // matched pieces fade out
    public static final int ANIM_FALL   = 1;  // remaining pieces fall

    public static final int REMOVE_TICKS = 10;
    public static final int FALL_TICKS   = 12;

    // ── Score popup ───────────────────────────────────────────────────────
    public static final int POPUP_TICKS = 45;

    // ── Public state (read by renderer) ──────────────────────────────────
    public int state;
    public int animPhase;
    public int animTick;
    public boolean isAnimating;

    public int cursorX, cursorY;
    public int selectedX = -1, selectedY = -1; // -1 = nothing selected

    public int score;
    public int moves;
    public int currentLevel;   // 0-based index into LevelConfig.ALL
    public LevelConfig levelCfg;

    // Score popup
    public int popupScore;
    public int popupCol, popupRow;
    public int popupTick;

    // ── Private ───────────────────────────────────────────────────────────
    private Piece[][] grid;
    private Random rng;

    // ── Constructor ───────────────────────────────────────────────────────
    public GameEngine() {
        rng  = new Random();
        grid = new Piece[COLS][ROWS];
        state = STATE_TITLE;
        currentLevel = 0;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Public accessors
    // ════════════════════════════════════════════════════════════════════════

    /** Returns the piece at (col, row), or null if out of bounds. */
    public Piece getPiece(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return null;
        return grid[col][row];
    }

    public boolean isLastLevel() {
        return currentLevel >= LevelConfig.TOTAL - 1;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Input handlers  (called by GameSurfaceView on key events)
    // ════════════════════════════════════════════════════════════════════════

    /** D-pad pressed. In selection mode the selected piece is swapped. */
    public void onDpad(int dx, int dy) {
        if (state == STATE_PLAYING && !isAnimating) {
            if (selectedX >= 0) {
                // A piece is selected – attempt swap in this direction
                int tx = selectedX + dx;
                int ty = selectedY + dy;
                trySwap(selectedX, selectedY, tx, ty);
                // Move cursor to swap destination whether swap succeeded or not
                cursorX = clampCol(selectedX + dx);
                cursorY = clampRow(selectedY + dy);
                selectedX = -1;
                selectedY = -1;
            } else {
                cursorX = clampCol(cursorX + dx);
                cursorY = clampRow(cursorY + dy);
            }
        }
    }

    /** A button: select / confirm. */
    public void onButtonA() {
        switch (state) {
            case STATE_TITLE:
                startLevel(0);
                break;

            case STATE_PLAYING:
                if (isAnimating) return;
                if (selectedX < 0) {
                    // Select piece under cursor
                    selectedX = cursorX;
                    selectedY = cursorY;
                } else if (selectedX == cursorX && selectedY == cursorY) {
                    // Tap same cell → deselect
                    selectedX = -1;
                    selectedY = -1;
                } else {
                    // Cursor moved to adjacent cell → swap
                    trySwap(selectedX, selectedY, cursorX, cursorY);
                    selectedX = -1;
                    selectedY = -1;
                }
                break;

            case STATE_WIN:
                if (isLastLevel()) {
                    state = STATE_ALL_CLEAR;
                } else {
                    startLevel(currentLevel + 1);
                }
                break;

            case STATE_GAME_OVER:
                startLevel(currentLevel);
                break;

            case STATE_ALL_CLEAR:
                state = STATE_TITLE;
                break;
        }
    }

    /** B button: cancel selection. */
    public void onButtonB() {
        if (state == STATE_PLAYING) {
            selectedX = -1;
            selectedY = -1;
        }
    }

    /** Start button: pause / resume. */
    public void onButtonStart() {
        if (state == STATE_PLAYING) {
            state = STATE_PAUSED;
        } else if (state == STATE_PAUSED) {
            state = STATE_PLAYING;
        } else if (state == STATE_TITLE) {
            startLevel(0);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Game loop update  (called every frame by the render thread)
    // ════════════════════════════════════════════════════════════════════════

    public void update() {
        // Popup timer
        if (popupTick > 0) popupTick--;

        if (!isAnimating) return;

        animTick++;

        if (animPhase == ANIM_REMOVE) {
            updateRemoveAnim();
        } else {
            updateFallAnim();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Private – animation steps
    // ════════════════════════════════════════════════════════════════════════

    private void updateRemoveAnim() {
        float progress = (float) animTick / REMOVE_TICKS;

        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                Piece p = grid[c][r];
                if (p != null && p.removing) {
                    p.removeAlpha = 1.0f - progress;
                }
            }
        }

        if (animTick >= REMOVE_TICKS) {
            // Actually remove pieces and score them
            int removed = 0;
            int sumCol = 0, sumRow = 0;
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (grid[c][r] != null && grid[c][r].removing) {
                        sumCol += c;
                        sumRow += r;
                        removed++;
                        grid[c][r] = null;
                    }
                }
            }
            // Score: 10 per piece + 5 bonus for each piece beyond 3
            int gained = removed * 10 + Math.max(0, removed - 3) * 5;
            score += gained;
            popupScore = gained;
            popupCol   = sumCol / removed;
            popupRow   = sumRow / removed;
            popupTick  = POPUP_TICKS;

            applyGravity();

            animPhase = ANIM_FALL;
            animTick  = 0;
        }
    }

    private void updateFallAnim() {
        if (animTick >= FALL_TICKS) {
            // Settle all pieces
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (grid[c][r] != null) grid[c][r].fallRows = 0;
                }
            }

            // Check for chain matches
            List<int[]> chains = findMatches();
            if (!chains.isEmpty()) {
                markRemoving(chains);
                animPhase = ANIM_REMOVE;
                animTick  = 0;
            } else {
                isAnimating = false;
                checkWinLose();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Private – core mechanics
    // ════════════════════════════════════════════════════════════════════════

    private void startLevel(int index) {
        currentLevel = index;
        levelCfg     = LevelConfig.ALL[index];
        score        = 0;
        moves        = levelCfg.maxMoves;
        cursorX      = COLS / 2;
        cursorY      = ROWS / 2;
        selectedX    = -1;
        selectedY    = -1;
        isAnimating  = false;
        animPhase    = ANIM_REMOVE;
        animTick     = 0;
        popupTick    = 0;
        fillGrid();
        state = STATE_PLAYING;
    }

    private void fillGrid() {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                grid[c][r] = new Piece(randomType());
            }
        }
        // Eliminate any accidental 3-in-a-rows in the initial fill
        resolveInitialMatches();
    }

    private void resolveInitialMatches() {
        boolean changed = true;
        int guard = 200;
        while (changed && guard-- > 0) {
            changed = false;
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (isPartOfMatch(c, r)) {
                        int oldType = grid[c][r].type;
                        for (int t = 1; t <= levelCfg.pieceTypes; t++) {
                            if (t != oldType) {
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

        // Horizontal run through (col, row)
        int hCount = 1;
        for (int c = col - 1; c >= 0 && grid[c][row].type == type; c--) hCount++;
        for (int c = col + 1; c < COLS && grid[c][row].type == type; c++) hCount++;
        if (hCount >= 3) return true;

        // Vertical run through (col, row)
        int vCount = 1;
        for (int r = row - 1; r >= 0 && grid[col][r].type == type; r--) vCount++;
        for (int r = row + 1; r < ROWS && grid[col][r].type == type; r++) vCount++;
        return vCount >= 3;
    }

    private void trySwap(int c1, int r1, int c2, int r2) {
        // Must be adjacent and within bounds
        if (c2 < 0 || c2 >= COLS || r2 < 0 || r2 >= ROWS) return;
        if (Math.abs(c1 - c2) + Math.abs(r1 - r2) != 1) return;

        // Swap
        Piece tmp = grid[c1][r1];
        grid[c1][r1] = grid[c2][r2];
        grid[c2][r2] = tmp;

        List<int[]> matches = findMatches();
        if (matches.isEmpty()) {
            // No match – swap back silently
            tmp = grid[c1][r1];
            grid[c1][r1] = grid[c2][r2];
            grid[c2][r2] = tmp;
        } else {
            moves--;
            markRemoving(matches);
            isAnimating = true;
            animPhase   = ANIM_REMOVE;
            animTick    = 0;
        }
    }

    /**
     * Returns all cells that are part of a 3-or-more match.
     */
    private List<int[]> findMatches() {
        boolean[][] matched = new boolean[COLS][ROWS];

        // Horizontal
        for (int r = 0; r < ROWS; r++) {
            int c = 0;
            while (c < COLS) {
                int type = grid[c][r].type;
                int len = 1;
                while (c + len < COLS && grid[c + len][r].type == type) len++;
                if (len >= 3) {
                    for (int k = 0; k < len; k++) matched[c + k][r] = true;
                }
                c += len;
            }
        }

        // Vertical
        for (int c = 0; c < COLS; c++) {
            int r = 0;
            while (r < ROWS) {
                int type = grid[c][r].type;
                int len = 1;
                while (r + len < ROWS && grid[c][r + len].type == type) len++;
                if (len >= 3) {
                    for (int k = 0; k < len; k++) matched[c][r + k] = true;
                }
                r += len;
            }
        }

        List<int[]> result = new ArrayList<int[]>();
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (matched[c][r]) result.add(new int[]{c, r});
            }
        }
        return result;
    }

    private void markRemoving(List<int[]> cells) {
        for (int[] pos : cells) {
            Piece p = grid[pos[0]][pos[1]];
            if (p != null) {
                p.removing    = true;
                p.removeAlpha = 1.0f;
            }
        }
    }

    /**
     * Pull pieces downward to fill gaps (null cells), then fill the
     * newly empty top cells with fresh random pieces.
     * Sets piece.fallRows so the renderer can animate the drop.
     */
    private void applyGravity() {
        for (int c = 0; c < COLS; c++) {
            // Compact non-null pieces to the bottom
            int write = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--) {
                Piece p = grid[c][r];
                if (p != null) {
                    int drop = write - r;
                    p.fallRows = drop;
                    p.removing = false;
                    p.removeAlpha = 1.0f;
                    grid[c][write] = p;
                    if (write != r) grid[c][r] = null;
                    write--;
                }
            }
            // Fill empty top rows with new pieces
            for (int r = write; r >= 0; r--) {
                Piece np = new Piece(randomType());
                np.fallRows = 0; // new pieces just appear (no fall animation)
                grid[c][r] = np;
            }
        }
    }

    private void checkWinLose() {
        if (score >= levelCfg.targetScore) {
            state = isLastLevel() ? STATE_ALL_CLEAR : STATE_WIN;
        } else if (moves <= 0) {
            state = STATE_GAME_OVER;
        }
        // (Could add deadlock detection here; omitted for simplicity)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════════

    private int randomType() {
        return rng.nextInt(levelCfg.pieceTypes) + 1;
    }

    private int clampCol(int c) { return Math.max(0, Math.min(COLS - 1, c)); }
    private int clampRow(int r) { return Math.max(0, Math.min(ROWS - 1, r)); }
}
