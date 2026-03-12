package com.kawaiimatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Rendering + input for Kawaii Match.
 *
 * Virtual resolution : VIRT_W × VIRT_H  (320 × 240)
 * The canvas is scaled from the real surface size to the virtual size,
 * so all drawing coordinates are in 320×240 space regardless of device DPI.
 *
 * Board layout (landscape):
 *   HUD_H  = 36 px  (top strip: level, score, moves, progress bar)
 *   CELL   = 34 px  (each grid cell, square)
 *   COLS   = 8, ROWS = 6  →  8×34 = 272 wide, 6×34 = 204 tall
 *   GRID_X = (320 − 272) / 2 = 24
 *   GRID_Y = 36
 *   Total height: 36 + 204 = 240 ✓
 */
public class GameSurfaceView extends SurfaceView
        implements SurfaceHolder.Callback, Runnable {

    // ── Virtual canvas size ───────────────────────────────────────────────
    static final int VIRT_W  = 320;
    static final int VIRT_H  = 240;

    // ── Board layout constants ────────────────────────────────────────────
    static final int HUD_H  = 36;
    static final int CELL   = 34;
    static final int GRID_X = (VIRT_W - GameEngine.COLS * CELL) / 2; // = 24
    static final int GRID_Y = HUD_H;

    // ── Piece palette  (index = piece type, 0 unused) ────────────────────
    private static final int[] COLOR_FILL = {
        0,
        0xFFFF6B9D,  // 1 HEART   pink
        0xFFFFD740,  // 2 STAR    golden yellow
        0xFF66BB6A,  // 3 LEAF    green
        0xFF42A5F5,  // 4 DROP    sky blue
        0xFFBA68C8,  // 5 GEM     purple
        0xFFFF8A65,  // 6 CANDY   orange
    };
    private static final int[] COLOR_DARK = {
        0,
        0xFFAD1457,  // 1
        0xFFF57F17,  // 2
        0xFF2E7D32,  // 3
        0xFF0D47A1,  // 4
        0xFF6A1B9A,  // 5
        0xFFBF360C,  // 6
    };

    // ── Threading ─────────────────────────────────────────────────────────
    private SurfaceHolder holder;
    private Thread thread;
    private volatile boolean running;

    // ── Game engine ───────────────────────────────────────────────────────
    private final GameEngine engine;

    // ── Pre-allocated paints (avoid GC in draw loop) ──────────────────────
    private final Paint pBg        = new Paint();
    private final Paint pHud       = new Paint();
    private final Paint pCell      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCellAlt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSelected  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDark      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextBig   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextSub   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOverlay   = new Paint();
    private final Paint pBar       = new Paint();
    private final Paint pBarBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pProgress  = new Paint();
    private final Paint pPopup     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine      = new Paint();
    private final Paint pStar      = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF  tmpRect = new RectF();
    private final Path   tmpPath = new Path();

    // ── Constructor ───────────────────────────────────────────────────────
    public GameSurfaceView(Context context) {
        super(context);
        engine = new GameEngine();
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        initPaints();
    }

    private void initPaints() {
        pBg.setColor(0xFF1A237E);

        pHud.setColor(0xFF0D1A5C);

        pCell.setColor(0x22FFFFFF);
        pCell.setStyle(Paint.Style.FILL);

        pCellAlt.setColor(0x11FFFFFF);
        pCellAlt.setStyle(Paint.Style.FILL);

        pCursor.setColor(0xFFFFFFFF);
        pCursor.setStyle(Paint.Style.STROKE);
        pCursor.setStrokeWidth(2f);

        pSelected.setColor(0xFFFFEA00);
        pSelected.setStyle(Paint.Style.STROKE);
        pSelected.setStrokeWidth(2.5f);

        pFill.setStyle(Paint.Style.FILL);
        pDark.setStyle(Paint.Style.FILL);
        pHighlight.setStyle(Paint.Style.FILL);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(11f);
        pText.setTypeface(Typeface.DEFAULT_BOLD);

        pTextBig.setColor(0xFFFFD740);
        pTextBig.setTextSize(22f);
        pTextBig.setTypeface(Typeface.DEFAULT_BOLD);
        pTextBig.setTextAlign(Paint.Align.CENTER);

        pTextSub.setColor(0xFFFFFFFF);
        pTextSub.setTextSize(12f);
        pTextSub.setTextAlign(Paint.Align.CENTER);

        pOverlay.setColor(0xCC000000);

        pBar.setColor(0x44000000);
        pBar.setStyle(Paint.Style.FILL);

        pBarBorder.setColor(0x66FFFFFF);
        pBarBorder.setStyle(Paint.Style.STROKE);
        pBarBorder.setStrokeWidth(1f);

        pProgress.setColor(0xFF00E676);
        pProgress.setStyle(Paint.Style.FILL);

        pPopup.setColor(0xFFFFFFFF);
        pPopup.setTextSize(15f);
        pPopup.setTypeface(Typeface.DEFAULT_BOLD);
        pPopup.setTextAlign(Paint.Align.CENTER);

        pLine.setColor(0x33FFFFFF);

        pStar.setStyle(Paint.Style.FILL);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SurfaceHolder.Callback
    // ════════════════════════════════════════════════════════════════════════

    @Override public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread  = new Thread(this);
        thread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int i) {}

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { thread.join(2000); } catch (InterruptedException ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Game loop
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void run() {
        final long TARGET_MS = 1000 / 60;
        while (running) {
            long t0 = System.currentTimeMillis();

            engine.update();

            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                float sx = canvas.getWidth()  / (float) VIRT_W;
                float sy = canvas.getHeight() / (float) VIRT_H;
                canvas.save();
                canvas.scale(sx, sy);
                draw(canvas);
                canvas.restore();
                holder.unlockCanvasAndPost(canvas);
            }

            long elapsed = System.currentTimeMillis() - t0;
            long sleep   = TARGET_MS - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Input
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    engine.onDpad( 0,-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  engine.onDpad( 0, 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  engine.onDpad(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: engine.onDpad( 1, 0); return true;

            // A button (confirm / select)
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
                engine.onButtonA(); return true;

            // B button (cancel)
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                engine.onButtonB(); return true;

            // Start (pause / resume)
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                engine.onButtonStart(); return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Master draw
    // ════════════════════════════════════════════════════════════════════════

    private void draw(Canvas c) {
        // Background
        c.drawColor(0xFF1A237E);

        int st = engine.state;

        if (st == GameEngine.STATE_TITLE) {
            drawTitle(c);
            return;
        }

        drawHUD(c);
        drawGrid(c);
        drawPieces(c);

        if (st == GameEngine.STATE_PLAYING || st == GameEngine.STATE_PAUSED) {
            drawCursor(c);
        }

        if (engine.popupTick > 0) drawPopup(c);

        // Overlays
        switch (st) {
            case GameEngine.STATE_WIN:       drawWin(c);      break;
            case GameEngine.STATE_GAME_OVER: drawGameOver(c); break;
            case GameEngine.STATE_ALL_CLEAR: drawAllClear(c); break;
            case GameEngine.STATE_PAUSED:    drawPaused(c);   break;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HUD (top strip)
    // ════════════════════════════════════════════════════════════════════════

    private void drawHUD(Canvas c) {
        c.drawRect(0, 0, VIRT_W, HUD_H, pHud);
        c.drawLine(0, HUD_H - 1, VIRT_W, HUD_H - 1, pLine);

        LevelConfig cfg = engine.levelCfg;

        // Level label
        pText.setTextSize(11f);
        pText.setColor(0xFFFFEA00);
        c.drawText("LV." + (engine.currentLevel + 1), 5, 13, pText);
        pText.setColor(0xFFFFFFFF);

        // Score
        c.drawText("SCORE", 5, 26, pText);
        pText.setColor(0xFFFFEA00);
        c.drawText("" + engine.score, 42, 26, pText);
        pText.setColor(0xFFFFFFFF);

        // Target
        c.drawText("TARGET", 88, 13, pText);
        pText.setColor(0xFFAED6F1);
        c.drawText("" + cfg.targetScore, 132, 13, pText);
        pText.setColor(0xFFFFFFFF);

        // Moves
        c.drawText("MOVES", 88, 26, pText);
        pText.setColor(engine.moves <= 5 ? 0xFFFF5252 : 0xFFFFFFFF);
        c.drawText("" + engine.moves, 127, 26, pText);
        pText.setColor(0xFFFFFFFF);

        // Progress bar  (right side of HUD)
        int barX = 165, barY = 8, barW = 148, barH = 12;
        c.drawRect(barX, barY, barX + barW, barY + barH, pBar);
        float frac = Math.min(1.0f, (float) engine.score / cfg.targetScore);
        c.drawRect(barX, barY, barX + (int)(barW * frac), barY + barH, pProgress);
        c.drawRect(barX, barY, barX + barW, barY + barH, pBarBorder);

        // Progress % text
        pText.setTextSize(9f);
        pText.setColor(0xFFFFFFFF);
        c.drawText((int)(frac * 100) + "%", barX + 4, barY + barH - 1, pText);

        // Bottom label
        pText.setTextSize(8f);
        pText.setColor(0xFFAAAAAA);
        c.drawText("A:select  dir:swap  B:cancel  Start:pause", 5, 35, pText);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Grid background cells
    // ════════════════════════════════════════════════════════════════════════

    private void drawGrid(Canvas c) {
        for (int col = 0; col < GameEngine.COLS; col++) {
            for (int row = 0; row < GameEngine.ROWS; row++) {
                float x = GRID_X + col * CELL;
                float y = GRID_Y + row * CELL;
                tmpRect.set(x + 1, y + 1, x + CELL - 1, y + CELL - 1);
                Paint p = ((col + row) % 2 == 0) ? pCell : pCellAlt;
                c.drawRoundRect(tmpRect, 5, 5, p);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Pieces
    // ════════════════════════════════════════════════════════════════════════

    private void drawPieces(Canvas c) {
        float fallProgress = 1.0f;
        if (engine.isAnimating && engine.animPhase == GameEngine.ANIM_FALL) {
            fallProgress = (float) engine.animTick / GameEngine.FALL_TICKS;
            if (fallProgress > 1.0f) fallProgress = 1.0f;
        }

        for (int col = 0; col < GameEngine.COLS; col++) {
            for (int row = 0; row < GameEngine.ROWS; row++) {
                Piece piece = engine.getPiece(col, row);
                if (piece == null || piece.type == Piece.EMPTY) continue;

                float finalY = GRID_Y + row * CELL;
                float drawY  = finalY - piece.fallRows * CELL * (1.0f - fallProgress);
                float cx     = GRID_X + col * CELL + CELL / 2.0f;
                float cy     = drawY + CELL / 2.0f;
                int   r      = CELL / 2 - 4;

                float alpha = piece.removing ? piece.removeAlpha : 1.0f;
                if (alpha < 0) alpha = 0;

                drawPiece(c, piece.type, cx, cy, r, alpha);
            }
        }
    }

    private void drawPiece(Canvas c, int type, float cx, float cy, int r, float alpha) {
        int a = (int)(255 * alpha);
        pFill.setColor(COLOR_FILL[type]);
        pFill.setAlpha(a);
        pDark.setColor(COLOR_DARK[type]);
        pDark.setAlpha(a);

        switch (type) {
            case Piece.HEART:  drawHeart (c, cx, cy, r, a); break;
            case Piece.STAR:   drawStar  (c, cx, cy, r, a); break;
            case Piece.LEAF:   drawLeaf  (c, cx, cy, r, a); break;
            case Piece.DROP:   drawDrop  (c, cx, cy, r, a); break;
            case Piece.GEM:    drawGem   (c, cx, cy, r, a); break;
            case Piece.CANDY:  drawCandy (c, cx, cy, r, a); break;
        }
    }

    // ── Individual piece shapes ──────────────────────────────────────────

    /** Pink heart: two circles + downward triangle. */
    private void drawHeart(Canvas c, float cx, float cy, int r, int alpha) {
        float bx = r * 0.48f;
        float by = cy - r * 0.12f;
        float br = r * 0.56f;

        pFill.setAlpha(alpha);
        c.drawCircle(cx - bx, by, br, pFill);
        c.drawCircle(cx + bx, by, br, pFill);

        tmpPath.reset();
        tmpPath.moveTo(cx - r * 0.95f, by);
        tmpPath.lineTo(cx,             cy + r * 0.88f);
        tmpPath.lineTo(cx + r * 0.95f, by);
        tmpPath.close();
        c.drawPath(tmpPath, pFill);

        // Shine dot
        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setAlpha((int)(alpha * 0.55f));
        c.drawCircle(cx - r * 0.3f, by - br * 0.45f, r * 0.18f, pHighlight);
    }

    /** Golden 5-pointed star. */
    private void drawStar(Canvas c, float cx, float cy, int r, int alpha) {
        tmpPath.reset();
        float innerR = r * 0.42f;
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5.0 - Math.PI / 2.0;
            float rad = (i % 2 == 0) ? r : innerR;
            float px  = cx + (float)(rad * Math.cos(angle));
            float py  = cy + (float)(rad * Math.sin(angle));
            if (i == 0) tmpPath.moveTo(px, py); else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        pFill.setAlpha(alpha);
        c.drawPath(tmpPath, pFill);

        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setAlpha((int)(alpha * 0.45f));
        c.drawCircle(cx - r * 0.1f, cy - r * 0.15f, r * 0.2f, pHighlight);
    }

    /** Green four-leaf-clover style oval rotated. */
    private void drawLeaf(Canvas c, float cx, float cy, int r, int alpha) {
        c.save();
        c.rotate(45, cx, cy);
        tmpRect.set(cx - r * 0.65f, cy - r, cx + r * 0.65f, cy + r);
        pFill.setAlpha(alpha);
        c.drawOval(tmpRect, pFill);
        c.restore();

        // Vein line
        pDark.setStyle(Paint.Style.STROKE);
        pDark.setStrokeWidth(1.5f);
        pDark.setAlpha((int)(alpha * 0.6f));
        c.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, pDark);
        pDark.setStyle(Paint.Style.FILL);

        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setAlpha((int)(alpha * 0.4f));
        c.drawCircle(cx - r * 0.2f, cy - r * 0.3f, r * 0.2f, pHighlight);
    }

    /** Blue teardrop. */
    private void drawDrop(Canvas c, float cx, float cy, int r, int alpha) {
        tmpPath.reset();
        tmpPath.moveTo(cx, cy - r);
        tmpPath.cubicTo(cx + r * 0.8f, cy - r * 0.2f,
                        cx + r * 0.8f, cy + r * 0.5f,
                        cx,            cy + r);
        tmpPath.cubicTo(cx - r * 0.8f, cy + r * 0.5f,
                        cx - r * 0.8f, cy - r * 0.2f,
                        cx,            cy - r);
        pFill.setAlpha(alpha);
        c.drawPath(tmpPath, pFill);

        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawCircle(cx - r * 0.25f, cy - r * 0.35f, r * 0.22f, pHighlight);
    }

    /** Purple hexagonal gem. */
    private void drawGem(Canvas c, float cx, float cy, int r, int alpha) {
        tmpPath.reset();
        tmpPath.moveTo(cx,            cy - r);
        tmpPath.lineTo(cx + r * 0.7f, cy - r * 0.3f);
        tmpPath.lineTo(cx + r * 0.7f, cy + r * 0.3f);
        tmpPath.lineTo(cx,            cy + r);
        tmpPath.lineTo(cx - r * 0.7f, cy + r * 0.3f);
        tmpPath.lineTo(cx - r * 0.7f, cy - r * 0.3f);
        tmpPath.close();
        pFill.setAlpha(alpha);
        c.drawPath(tmpPath, pFill);

        // Inner highlight lines
        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setStyle(Paint.Style.STROKE);
        pHighlight.setStrokeWidth(1.5f);
        pHighlight.setAlpha((int)(alpha * 0.55f));
        c.drawLine(cx - r * 0.35f, cy - r * 0.65f, cx + r * 0.35f, cy - r * 0.65f, pHighlight);
        c.drawLine(cx - r * 0.6f,  cy,              cx,              cy + r * 0.6f,  pHighlight);
        c.drawLine(cx + r * 0.6f,  cy,              cx,              cy + r * 0.6f,  pHighlight);
        pHighlight.setStyle(Paint.Style.FILL);
    }

    /** Orange candy circle with diagonal stripes. */
    private void drawCandy(Canvas c, float cx, float cy, int r, int alpha) {
        pFill.setAlpha(alpha);
        c.drawCircle(cx, cy, r, pFill);

        // Stripes (clip to circle)
        c.save();
        tmpPath.reset();
        tmpPath.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(tmpPath);

        pDark.setStyle(Paint.Style.STROKE);
        pDark.setStrokeWidth(3.5f);
        pDark.setAlpha((int)(alpha * 0.45f));
        c.rotate(45, cx, cy);
        for (int i = -2; i <= 2; i++) {
            float off = i * r * 0.65f;
            c.drawLine(cx + off - r * 1.5f, cy - r * 1.5f,
                       cx + off + r * 1.5f, cy + r * 1.5f, pDark);
        }
        pDark.setStyle(Paint.Style.FILL);
        c.restore();

        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.25f, pHighlight);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Cursor
    // ════════════════════════════════════════════════════════════════════════

    private void drawCursor(Canvas c) {
        // Animate cursor alpha with a sine pulse
        long time = System.currentTimeMillis();
        float blink = (float) Math.abs(Math.sin(time / 280.0));
        int curAlpha = 140 + (int)(115 * blink);

        // Selected-piece highlight (bright yellow box)
        if (engine.selectedX >= 0) {
            float sx = GRID_X + engine.selectedX * CELL;
            float sy = GRID_Y + engine.selectedY * CELL;
            pSelected.setAlpha(curAlpha);
            tmpRect.set(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1);
            c.drawRoundRect(tmpRect, 5, 5, pSelected);
        }

        // Cursor box
        float cx = GRID_X + engine.cursorX * CELL;
        float cy = GRID_Y + engine.cursorY * CELL;
        pCursor.setAlpha(curAlpha);
        tmpRect.set(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1);
        c.drawRoundRect(tmpRect, 5, 5, pCursor);

        // Corner brackets
        int b = 6;
        pCursor.setStrokeWidth(2.5f);
        pCursor.setAlpha(255);
        float x1 = cx + 2, y1 = cy + 2;
        float x2 = cx + CELL - 2, y2 = cy + CELL - 2;
        // top-left
        c.drawLine(x1, y1, x1 + b, y1, pCursor);
        c.drawLine(x1, y1, x1, y1 + b, pCursor);
        // top-right
        c.drawLine(x2, y1, x2 - b, y1, pCursor);
        c.drawLine(x2, y1, x2, y1 + b, pCursor);
        // bottom-left
        c.drawLine(x1, y2, x1 + b, y2, pCursor);
        c.drawLine(x1, y2, x1, y2 - b, pCursor);
        // bottom-right
        c.drawLine(x2, y2, x2 - b, y2, pCursor);
        c.drawLine(x2, y2, x2, y2 - b, pCursor);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Score popup
    // ════════════════════════════════════════════════════════════════════════

    private void drawPopup(Canvas c) {
        float progress = (float) engine.popupTick / GameEngine.POPUP_TICKS;
        float px = GRID_X + engine.popupCol * CELL + CELL / 2.0f;
        float py = GRID_Y + engine.popupRow * CELL - (1.0f - progress) * 22;
        pPopup.setAlpha((int)(255 * progress));
        c.drawText("+" + engine.popupScore, px, py, pPopup);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Title screen
    // ════════════════════════════════════════════════════════════════════════

    private void drawTitle(Canvas c) {
        // Gradient-ish background stripes
        for (int i = 0; i < VIRT_H; i++) {
            pLine.setColor(Color.rgb(26 + i / 12, 35 + i / 10, 100 + i / 5));
            c.drawLine(0, i, VIRT_W, i, pLine);
        }

        // Decorative pieces row
        int[] demo = {Piece.HEART, Piece.STAR, Piece.LEAF, Piece.DROP, Piece.GEM, Piece.CANDY,
                      Piece.HEART, Piece.STAR};
        for (int i = 0; i < 8; i++) {
            drawPiece(c, demo[i], 20 + i * 40, 40, 13, 0.35f);
            drawPiece(c, demo[(i + 3) % 6 + 1], 20 + i * 40, VIRT_H - 40, 13, 0.35f);
        }

        // Title
        pTextBig.setColor(0xFFFFEA00);
        pTextBig.setTextSize(26f);
        c.drawText("可爱消消乐", VIRT_W / 2.0f, 90, pTextBig);

        pTextBig.setColor(0xFFFFFFFF);
        pTextBig.setTextSize(14f);
        c.drawText("Kawaii Match", VIRT_W / 2.0f, 110, pTextBig);

        // Subtitle
        pTextSub.setColor(0xFFB0BEC5);
        pTextSub.setTextSize(10f);
        c.drawText("20 Levels  ·  Match 3 or more to score!", VIRT_W / 2.0f, 132, pTextSub);

        // Blink "press start"
        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF);
            pTextSub.setTextSize(12f);
            c.drawText("Press  START  or  A  to play", VIRT_W / 2.0f, 170, pTextSub);
        }

        // Controls hint
        pTextSub.setColor(0xFF78909C);
        pTextSub.setTextSize(9f);
        c.drawText("D-pad: move  ·  A: select  ·  dir after select: swap  ·  B: cancel", VIRT_W / 2.0f, 200, pTextSub);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Overlay screens
    // ════════════════════════════════════════════════════════════════════════

    private void drawWin(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);

        // Stars
        int stars = computeStars();
        drawStarRow(c, VIRT_W / 2.0f, 55, stars);

        pTextBig.setColor(0xFFFFEA00);
        pTextBig.setTextSize(22f);
        c.drawText("LEVEL  CLEAR!", VIRT_W / 2.0f, 95, pTextBig);

        pTextSub.setColor(0xFFFFFFFF);
        pTextSub.setTextSize(12f);
        c.drawText("Score: " + engine.score + "  /  " + engine.levelCfg.targetScore, VIRT_W / 2.0f, 118, pTextSub);

        if (!engine.isLastLevel()) {
            pTextSub.setColor(0xFFB2EBF2);
            pTextSub.setTextSize(11f);
            c.drawText("Level " + (engine.currentLevel + 2) + "  next!", VIRT_W / 2.0f, 138, pTextSub);
        }

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF);
            pTextSub.setTextSize(11f);
            c.drawText("Press A to continue", VIRT_W / 2.0f, 168, pTextSub);
        }
    }

    private void drawGameOver(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);

        pTextBig.setColor(0xFFFF5252);
        pTextBig.setTextSize(24f);
        c.drawText("GAME  OVER", VIRT_W / 2.0f, 95, pTextBig);

        pTextSub.setColor(0xFFFFFFFF);
        pTextSub.setTextSize(11f);
        c.drawText("Score: " + engine.score
                + "  ·  Need: " + engine.levelCfg.targetScore, VIRT_W / 2.0f, 120, pTextSub);

        int gap = engine.levelCfg.targetScore - engine.score;
        pTextSub.setColor(0xFFFFCDD2);
        pTextSub.setTextSize(10f);
        c.drawText("Just " + gap + " more points needed!", VIRT_W / 2.0f, 138, pTextSub);

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF);
            pTextSub.setTextSize(11f);
            c.drawText("Press A to retry", VIRT_W / 2.0f, 168, pTextSub);
        }
    }

    private void drawAllClear(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);

        drawStarRow(c, VIRT_W / 2.0f, 50, 3);

        pTextBig.setColor(0xFFFFEA00);
        pTextBig.setTextSize(20f);
        c.drawText("ALL  LEVELS  CLEAR!", VIRT_W / 2.0f, 90, pTextBig);

        pTextSub.setColor(0xFFFFFFFF);
        pTextSub.setTextSize(12f);
        c.drawText("You are a Kawaii Master!", VIRT_W / 2.0f, 115, pTextSub);

        pTextSub.setColor(0xFFB2EBF2);
        pTextSub.setTextSize(10f);
        c.drawText("Final Score: " + engine.score, VIRT_W / 2.0f, 135, pTextSub);

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF);
            pTextSub.setTextSize(11f);
            c.drawText("Press A to return to title", VIRT_W / 2.0f, 168, pTextSub);
        }
    }

    private void drawPaused(Canvas c) {
        pOverlay.setColor(0x88000000);
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);
        pOverlay.setColor(0xCC000000);

        pTextBig.setColor(0xFFFFFFFF);
        pTextBig.setTextSize(26f);
        c.drawText("PAUSED", VIRT_W / 2.0f, VIRT_H / 2.0f - 10, pTextBig);

        pTextSub.setColor(0xFFAABBCC);
        pTextSub.setTextSize(11f);
        c.drawText("Press START to resume", VIRT_W / 2.0f, VIRT_H / 2.0f + 16, pTextSub);
    }

    // ── Helper: star rating row ──────────────────────────────────────────

    private int computeStars() {
        float ratio = (float) engine.score / engine.levelCfg.targetScore;
        if (ratio >= 1.8f) return 3;
        if (ratio >= 1.3f) return 2;
        return 1;
    }

    private void drawStarRow(Canvas c, float cx, float top, int filled) {
        for (int i = 0; i < 3; i++) {
            float sx = cx - 30 + i * 30;
            pStar.setColor(i < filled ? 0xFFFFD740 : 0xFF555566);
            drawStarShape(c, sx, top, 11, pStar);
        }
    }

    private void drawStarShape(Canvas c, float cx, float cy, float r, Paint p) {
        tmpPath.reset();
        float innerR = r * 0.42f;
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5.0 - Math.PI / 2.0;
            float rad = (i % 2 == 0) ? r : innerR;
            float px  = cx + (float)(rad * Math.cos(angle));
            float py  = cy + (float)(rad * Math.sin(angle));
            if (i == 0) tmpPath.moveTo(px, py); else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        c.drawPath(tmpPath, p);
    }
}
