package com.kawaiimatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Rendering + input – optimised version.
 *
 * Visual improvements vs. v1:
 *  • Cute face (eyes + smile) drawn on every piece
 *  • Selected piece pulses with a subtle scale animation
 *  • Red flash overlay on cells involved in an invalid swap
 *  • "COMBO ×N!" overlay with score-multiplier hint during chain reactions
 *  • "♻ 洗牌!" notification when the board is auto-shuffled
 *  • High-score shown on the Level-Clear screen
 *  • Direction-hint arrows shown around the selected piece
 */
public class GameSurfaceView extends SurfaceView
        implements SurfaceHolder.Callback, Runnable {

    // ── Virtual resolution ────────────────────────────────────────────────
    static final int VIRT_W = 320;
    static final int VIRT_H = 240;

    // ── Board layout ──────────────────────────────────────────────────────
    static final int HUD_H  = 36;
    static final int CELL   = 34;
    static final int GRID_X = (VIRT_W - GameEngine.COLS * CELL) / 2; // 24
    static final int GRID_Y = HUD_H;                                   // 36

    // ── Piece colours ─────────────────────────────────────────────────────
    private static final int[] COLOR_FILL = {
        0,
        0xFFFF6B9D,  // 1 HEART  pink
        0xFFFFD740,  // 2 STAR   gold
        0xFF66BB6A,  // 3 LEAF   green
        0xFF42A5F5,  // 4 DROP   blue
        0xFFBA68C8,  // 5 GEM    purple
        0xFFFF8A65,  // 6 CANDY  orange
    };
    private static final int[] COLOR_DARK = {
        0,
        0xFFAD1457,
        0xFFF57F17,
        0xFF2E7D32,
        0xFF0D47A1,
        0xFF6A1B9A,
        0xFFBF360C,
    };

    // ── Threading ─────────────────────────────────────────────────────────
    private SurfaceHolder  holder;
    private Thread         thread;
    private volatile boolean running;

    // ── Engine ────────────────────────────────────────────────────────────
    private final GameEngine engine;

    // ── Pre-allocated paints (no GC in draw loop) ─────────────────────────
    private final Paint pHud       = new Paint();
    private final Paint pCell      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCellAlt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSelected  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDark      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFace      = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final Paint pFlash     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCombo     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShuffleMsg= new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable geometry
    private final RectF tmpRect = new RectF();
    private final Path  tmpPath = new Path();

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
        pHud.setColor(0xFF0D1A5C);

        pCell.setColor(0x22FFFFFF); pCell.setStyle(Paint.Style.FILL);
        pCellAlt.setColor(0x11FFFFFF); pCellAlt.setStyle(Paint.Style.FILL);

        pCursor.setColor(0xFFFFFFFF);
        pCursor.setStyle(Paint.Style.STROKE);
        pCursor.setStrokeWidth(2f);

        pSelected.setColor(0xFFFFEA00);
        pSelected.setStyle(Paint.Style.STROKE);
        pSelected.setStrokeWidth(2.5f);

        pFill.setStyle(Paint.Style.FILL);
        pDark.setStyle(Paint.Style.FILL);
        pHighlight.setStyle(Paint.Style.FILL);

        pFace.setStyle(Paint.Style.FILL);
        pFace.setStrokeCap(Paint.Cap.ROUND);

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

        pBar.setColor(0x44000000); pBar.setStyle(Paint.Style.FILL);
        pBarBorder.setColor(0x66FFFFFF);
        pBarBorder.setStyle(Paint.Style.STROKE);
        pBarBorder.setStrokeWidth(1f);
        pProgress.setColor(0xFF00E676); pProgress.setStyle(Paint.Style.FILL);

        pPopup.setColor(0xFFFFFFFF);
        pPopup.setTextSize(15f);
        pPopup.setTypeface(Typeface.DEFAULT_BOLD);
        pPopup.setTextAlign(Paint.Align.CENTER);

        pLine.setColor(0x33FFFFFF);
        pStar.setStyle(Paint.Style.FILL);

        pFlash.setStyle(Paint.Style.FILL);
        pCombo.setTypeface(Typeface.DEFAULT_BOLD);
        pCombo.setTextAlign(Paint.Align.CENTER);
        pShuffleMsg.setTypeface(Typeface.DEFAULT_BOLD);
        pShuffleMsg.setTextAlign(Paint.Align.CENTER);
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
            long sleep = TARGET_MS - (System.currentTimeMillis() - t0);
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
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
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:      engine.onButtonA();   return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:       engine.onButtonB();   return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:       engine.onButtonStart(); return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Master draw
    // ════════════════════════════════════════════════════════════════════════

    private void draw(Canvas c) {
        c.drawColor(0xFF1A237E);

        if (engine.state == GameEngine.STATE_TITLE) { drawTitle(c); return; }

        drawHUD(c);
        drawGrid(c);
        drawPieces(c);
        drawInvalidFlash(c);

        int st = engine.state;
        if (st == GameEngine.STATE_PLAYING || st == GameEngine.STATE_PAUSED)
            drawCursor(c);

        if (engine.popupTick > 0)       drawPopup(c);
        if (engine.comboDisplayTick > 0) drawComboOverlay(c);
        if (engine.showShuffleMsg)       drawShuffleMsg(c);

        switch (st) {
            case GameEngine.STATE_WIN:       drawWin(c);      break;
            case GameEngine.STATE_GAME_OVER: drawGameOver(c); break;
            case GameEngine.STATE_ALL_CLEAR: drawAllClear(c); break;
            case GameEngine.STATE_PAUSED:    drawPaused(c);   break;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HUD
    // ════════════════════════════════════════════════════════════════════════

    private void drawHUD(Canvas c) {
        c.drawRect(0, 0, VIRT_W, HUD_H, pHud);
        c.drawLine(0, HUD_H - 1, VIRT_W, HUD_H - 1, pLine);

        LevelConfig cfg = engine.levelCfg;

        pText.setTextSize(11f); pText.setColor(0xFFFFEA00);
        c.drawText("LV." + (engine.currentLevel + 1), 5, 13, pText);

        pText.setColor(0xFFFFFFFF);
        c.drawText("SCORE", 5, 26, pText);
        pText.setColor(0xFFFFEA00);
        c.drawText("" + engine.score, 42, 26, pText);

        pText.setColor(0xFFFFFFFF);
        c.drawText("TARGET", 88, 13, pText);
        pText.setColor(0xFFAED6F1);
        c.drawText("" + cfg.targetScore, 132, 13, pText);

        pText.setColor(0xFFFFFFFF);
        c.drawText("MOVES", 88, 26, pText);
        pText.setColor(engine.moves <= 5 ? 0xFFFF5252 : 0xFFFFFFFF);
        c.drawText("" + engine.moves, 127, 26, pText);

        // Progress bar
        int bx = 165, by = 8, bw = 148, bh = 12;
        c.drawRect(bx, by, bx + bw, by + bh, pBar);
        float frac = Math.min(1f, (float) engine.score / cfg.targetScore);
        c.drawRect(bx, by, bx + (int)(bw * frac), by + bh, pProgress);
        c.drawRect(bx, by, bx + bw, by + bh, pBarBorder);
        pText.setTextSize(9f); pText.setColor(0xFFFFFFFF);
        c.drawText((int)(frac * 100) + "%", bx + 4, by + bh - 1, pText);

        pText.setTextSize(8f); pText.setColor(0xFFAAAAAA);
        c.drawText("A:select  dir:swap  B:cancel  Start:pause", 5, 35, pText);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Grid cells
    // ════════════════════════════════════════════════════════════════════════

    private void drawGrid(Canvas c) {
        for (int col = 0; col < GameEngine.COLS; col++) {
            for (int row = 0; row < GameEngine.ROWS; row++) {
                float x = GRID_X + col * CELL, y = GRID_Y + row * CELL;
                tmpRect.set(x + 1, y + 1, x + CELL - 1, y + CELL - 1);
                c.drawRoundRect(tmpRect, 5, 5, (col + row) % 2 == 0 ? pCell : pCellAlt);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Pieces
    // ════════════════════════════════════════════════════════════════════════

    private void drawPieces(Canvas c) {
        float fallProg = 1f;
        if (engine.isAnimating && engine.animPhase == GameEngine.ANIM_FALL)
            fallProg = Math.min(1f, (float) engine.animTick / GameEngine.FALL_TICKS);

        long now = System.currentTimeMillis();

        for (int col = 0; col < GameEngine.COLS; col++) {
            for (int row = 0; row < GameEngine.ROWS; row++) {
                Piece piece = engine.getPiece(col, row);
                if (piece == null || piece.type == Piece.EMPTY) continue;

                float finalY = GRID_Y + row * CELL;
                float drawY  = finalY - piece.fallRows * CELL * (1f - fallProg);
                float baseCX = GRID_X + col * CELL + CELL / 2f;
                float baseCY = drawY + CELL / 2f;

                // Selected piece: subtle scale pulse
                boolean isSelected = (col == engine.selectedX && row == engine.selectedY);
                float scale = 1f;
                if (isSelected)
                    scale = 1f + 0.10f * (float) Math.abs(Math.sin(now / 190.0));

                int r = (int)((CELL / 2 - 4) * scale);
                float alpha = piece.removing ? Math.max(0f, piece.removeAlpha) : 1f;

                drawPiece(c, piece.type, baseCX, baseCY, r, alpha);
            }
        }
    }

    private void drawPiece(Canvas c, int type, float cx, float cy, int r, float alpha) {
        int a = (int)(255 * alpha);
        pFill.setColor(COLOR_FILL[type]); pFill.setAlpha(a);
        pDark.setColor(COLOR_DARK[type]); pDark.setAlpha(a);

        switch (type) {
            case Piece.HEART: drawHeart(c, cx, cy, r, a); break;
            case Piece.STAR:  drawStar (c, cx, cy, r, a); break;
            case Piece.LEAF:  drawLeaf (c, cx, cy, r, a); break;
            case Piece.DROP:  drawDrop (c, cx, cy, r, a); break;
            case Piece.GEM:   drawGem  (c, cx, cy, r, a); break;
            case Piece.CANDY: drawCandy(c, cx, cy, r, a); break;
        }

        // Cute face on every piece
        drawFace(c, cx, cy, r, COLOR_DARK[type], a);
    }

    // ── Piece shapes ─────────────────────────────────────────────────────

    private void drawHeart(Canvas c, float cx, float cy, int r, int alpha) {
        float bx = r * 0.48f, by = cy - r * 0.12f, br = r * 0.56f;
        pFill.setAlpha(alpha);
        c.drawCircle(cx - bx, by, br, pFill);
        c.drawCircle(cx + bx, by, br, pFill);
        tmpPath.reset();
        tmpPath.moveTo(cx - r * 0.95f, by);
        tmpPath.lineTo(cx,             cy + r * 0.88f);
        tmpPath.lineTo(cx + r * 0.95f, by);
        tmpPath.close();
        c.drawPath(tmpPath, pFill);
        pHighlight.setColor(0xFFFFFFFF); pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawCircle(cx - r * 0.3f, by - br * 0.42f, r * 0.17f, pHighlight);
    }

    private void drawStar(Canvas c, float cx, float cy, int r, int alpha) {
        tmpPath.reset();
        float inner = r * 0.42f;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0 - Math.PI / 2.0;
            float  rad = (i % 2 == 0) ? r : inner;
            float  px = cx + (float)(rad * Math.cos(a));
            float  py = cy + (float)(rad * Math.sin(a));
            if (i == 0) tmpPath.moveTo(px, py); else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        pFill.setAlpha(alpha);
        c.drawPath(tmpPath, pFill);
        pHighlight.setColor(0xFFFFFFFF); pHighlight.setAlpha((int)(alpha * 0.45f));
        c.drawCircle(cx - r * 0.1f, cy - r * 0.18f, r * 0.2f, pHighlight);
    }

    private void drawLeaf(Canvas c, float cx, float cy, int r, int alpha) {
        c.save();
        c.rotate(45, cx, cy);
        tmpRect.set(cx - r * 0.65f, cy - r, cx + r * 0.65f, cy + r);
        pFill.setAlpha(alpha);
        c.drawOval(tmpRect, pFill);
        c.restore();
        pDark.setStyle(Paint.Style.STROKE); pDark.setStrokeWidth(1.5f); pDark.setAlpha((int)(alpha * 0.5f));
        c.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.65f, pDark);
        pDark.setStyle(Paint.Style.FILL);
        pHighlight.setColor(0xFFFFFFFF); pHighlight.setAlpha((int)(alpha * 0.35f));
        c.drawCircle(cx - r * 0.2f, cy - r * 0.3f, r * 0.2f, pHighlight);
    }

    private void drawDrop(Canvas c, float cx, float cy, int r, int alpha) {
        tmpPath.reset();
        tmpPath.moveTo(cx, cy - r);
        tmpPath.cubicTo(cx + r * 0.8f, cy - r * 0.2f, cx + r * 0.8f, cy + r * 0.5f, cx, cy + r);
        tmpPath.cubicTo(cx - r * 0.8f, cy + r * 0.5f, cx - r * 0.8f, cy - r * 0.2f, cx, cy - r);
        pFill.setAlpha(alpha);
        c.drawPath(tmpPath, pFill);
        pHighlight.setColor(0xFFFFFFFF); pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawCircle(cx - r * 0.25f, cy - r * 0.32f, r * 0.2f, pHighlight);
    }

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
        pHighlight.setColor(0xFFFFFFFF);
        pHighlight.setStyle(Paint.Style.STROKE);
        pHighlight.setStrokeWidth(1.5f);
        pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawLine(cx - r * 0.35f, cy - r * 0.62f, cx + r * 0.35f, cy - r * 0.62f, pHighlight);
        c.drawLine(cx - r * 0.58f, cy,              cx,              cy + r * 0.58f, pHighlight);
        c.drawLine(cx + r * 0.58f, cy,              cx,              cy + r * 0.58f, pHighlight);
        pHighlight.setStyle(Paint.Style.FILL);
    }

    private void drawCandy(Canvas c, float cx, float cy, int r, int alpha) {
        pFill.setAlpha(alpha);
        c.drawCircle(cx, cy, r, pFill);
        c.save();
        tmpPath.reset();
        tmpPath.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(tmpPath);
        pDark.setStyle(Paint.Style.STROKE); pDark.setStrokeWidth(3.5f); pDark.setAlpha((int)(alpha * 0.4f));
        c.rotate(45, cx, cy);
        for (int i = -2; i <= 2; i++) {
            float off = i * r * 0.65f;
            c.drawLine(cx + off - r * 1.5f, cy - r * 1.5f, cx + off + r * 1.5f, cy + r * 1.5f, pDark);
        }
        pDark.setStyle(Paint.Style.FILL);
        c.restore();
        pHighlight.setColor(0xFFFFFFFF); pHighlight.setAlpha((int)(alpha * 0.5f));
        c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.25f, pHighlight);
    }

    // ── Cute face ─────────────────────────────────────────────────────────

    /**
     * Draws two small eyes and a smile arc at the given face-centre position.
     * faceR controls the overall face scaling (typically 45–55 % of piece radius).
     */
    private void drawFace(Canvas c, float cx, float cy, int pieceR, int darkColor, int alpha) {
        // Face centre: slightly below piece centre for hearts/drops, otherwise centre
        float faceR  = pieceR * 0.52f;
        float faceCY = cy + pieceR * 0.05f;

        int faceAlpha = (int)(alpha * 0.80f);

        // Eyes
        pFace.setStyle(Paint.Style.FILL);
        pFace.setColor(darkColor);
        pFace.setAlpha(faceAlpha);
        float eyeSize = Math.max(1.2f, faceR * 0.18f);
        float eyeX    = faceR * 0.32f;
        float eyeY    = faceR * 0.20f;
        c.drawCircle(cx - eyeX, faceCY - eyeY, eyeSize, pFace);
        c.drawCircle(cx + eyeX, faceCY - eyeY, eyeSize, pFace);

        // Smile arc
        pFace.setStyle(Paint.Style.STROKE);
        pFace.setStrokeWidth(Math.max(1f, faceR * 0.16f));
        pFace.setAlpha((int)(alpha * 0.70f));
        float smileHW = faceR * 0.35f;
        float smileH  = faceR * 0.28f;
        float smileTop = faceCY + eyeY * 0.5f;
        tmpRect.set(cx - smileHW, smileTop, cx + smileHW, smileTop + smileH);
        c.drawArc(tmpRect, 0, 180, false, pFace);
        pFace.setStyle(Paint.Style.FILL);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Cursor + selection
    // ════════════════════════════════════════════════════════════════════════

    private void drawCursor(Canvas c) {
        long time  = System.currentTimeMillis();
        float blink = (float) Math.abs(Math.sin(time / 280.0));
        int curAlpha = 140 + (int)(115 * blink);

        // Yellow box around selected piece
        if (engine.selectedX >= 0) {
            float sx = GRID_X + engine.selectedX * CELL;
            float sy = GRID_Y + engine.selectedY * CELL;
            pSelected.setAlpha(curAlpha);
            tmpRect.set(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1);
            c.drawRoundRect(tmpRect, 5, 5, pSelected);
            // Direction-hint arrows (tiny triangles on each side)
            drawDirectionHints(c, sx, sy, curAlpha);
        }

        // White cursor box
        float cx = GRID_X + engine.cursorX * CELL;
        float cy = GRID_Y + engine.cursorY * CELL;
        pCursor.setAlpha(curAlpha);
        tmpRect.set(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1);
        c.drawRoundRect(tmpRect, 5, 5, pCursor);

        // Corner brackets
        int b = 6;
        pCursor.setStrokeWidth(2.5f); pCursor.setAlpha(255);
        float x1 = cx + 2, y1 = cy + 2, x2 = cx + CELL - 2, y2 = cy + CELL - 2;
        c.drawLine(x1, y1, x1 + b, y1, pCursor); c.drawLine(x1, y1, x1, y1 + b, pCursor);
        c.drawLine(x2, y1, x2 - b, y1, pCursor); c.drawLine(x2, y1, x2, y1 + b, pCursor);
        c.drawLine(x1, y2, x1 + b, y2, pCursor); c.drawLine(x1, y2, x1, y2 - b, pCursor);
        c.drawLine(x2, y2, x2 - b, y2, pCursor); c.drawLine(x2, y2, x2, y2 - b, pCursor);
    }

    /** Small arrows on the 4 sides of the selected cell to hint swap directions. */
    private void drawDirectionHints(Canvas c, float sx, float sy, int alpha) {
        Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setColor(0xFFFFEA00);
        ap.setAlpha(alpha / 2);
        ap.setStyle(Paint.Style.FILL);
        float mid = CELL / 2f, tip = 5f, base = 3f;
        // Up
        if (engine.selectedY > 0) {
            tmpPath.reset();
            tmpPath.moveTo(sx + mid, sy - 2);
            tmpPath.lineTo(sx + mid - base, sy - 2 - tip);
            tmpPath.lineTo(sx + mid + base, sy - 2 - tip);
            tmpPath.close();
            c.drawPath(tmpPath, ap);
        }
        // Down
        if (engine.selectedY < GameEngine.ROWS - 1) {
            tmpPath.reset();
            tmpPath.moveTo(sx + mid, sy + CELL + 2);
            tmpPath.lineTo(sx + mid - base, sy + CELL + 2 + tip);
            tmpPath.lineTo(sx + mid + base, sy + CELL + 2 + tip);
            tmpPath.close();
            c.drawPath(tmpPath, ap);
        }
        // Left
        if (engine.selectedX > 0) {
            tmpPath.reset();
            tmpPath.moveTo(sx - 2, sy + mid);
            tmpPath.lineTo(sx - 2 - tip, sy + mid - base);
            tmpPath.lineTo(sx - 2 - tip, sy + mid + base);
            tmpPath.close();
            c.drawPath(tmpPath, ap);
        }
        // Right
        if (engine.selectedX < GameEngine.COLS - 1) {
            tmpPath.reset();
            tmpPath.moveTo(sx + CELL + 2, sy + mid);
            tmpPath.lineTo(sx + CELL + 2 + tip, sy + mid - base);
            tmpPath.lineTo(sx + CELL + 2 + tip, sy + mid + base);
            tmpPath.close();
            c.drawPath(tmpPath, ap);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Score popup
    // ════════════════════════════════════════════════════════════════════════

    private void drawPopup(Canvas c) {
        float prog = (float) engine.popupTick / GameEngine.POPUP_TICKS;
        float px   = GRID_X + engine.popupCol * CELL + CELL / 2f;
        float py   = GRID_Y + engine.popupRow * CELL - (1f - prog) * 22;
        pPopup.setAlpha((int)(255 * prog));
        c.drawText("+" + engine.popupScore, px, py, pPopup);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Invalid-swap flash
    // ════════════════════════════════════════════════════════════════════════

    private void drawInvalidFlash(Canvas c) {
        if (engine.invalidTick <= 0) return;
        float prog  = (float) engine.invalidTick / GameEngine.INVALID_TICKS;
        float pulse = (float)(0.5 + 0.5 * Math.sin(engine.invalidTick * Math.PI * 0.6));
        int   alpha = (int)(190 * prog * pulse);

        pFlash.setColor(0xFFFF3333); pFlash.setAlpha(alpha);

        if (engine.invalidCol1 >= 0) {
            float x = GRID_X + engine.invalidCol1 * CELL;
            float y = GRID_Y + engine.invalidRow1 * CELL;
            tmpRect.set(x + 1, y + 1, x + CELL - 1, y + CELL - 1);
            c.drawRoundRect(tmpRect, 5, 5, pFlash);
        }
        if (engine.invalidCol2 >= 0) {
            float x = GRID_X + engine.invalidCol2 * CELL;
            float y = GRID_Y + engine.invalidRow2 * CELL;
            tmpRect.set(x + 1, y + 1, x + CELL - 1, y + CELL - 1);
            c.drawRoundRect(tmpRect, 5, 5, pFlash);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Combo overlay
    // ════════════════════════════════════════════════════════════════════════

    private void drawComboOverlay(Canvas c) {
        float prog = (float) engine.comboDisplayTick / GameEngine.COMBO_TICKS;
        // Fade: 1.0→0.8 = fade in, 0.8→0.2 = full, 0.2→0 = fade out
        float a = prog > 0.8f ? (1f - prog) / 0.2f
                : prog < 0.2f ? prog / 0.2f : 1f;
        float scale = 1f + 0.18f * (float) Math.sin(prog * Math.PI);

        String label = "COMBO  x" + engine.comboDisplayCount + "!";
        String multi = engine.comboDisplayCount >= 4 ? "Score x2.5!"
                     : engine.comboDisplayCount == 3 ? "Score x2.0!"
                     : "Score x1.5!";

        float textSize = 20f * scale;

        // Shadow
        pCombo.setColor(0xFF000000); pCombo.setAlpha((int)(160 * a));
        pCombo.setTextSize(textSize);
        c.drawText(label, VIRT_W / 2f + 2, VIRT_H / 2f + 2, pCombo);

        // Main text – golden yellow
        pCombo.setColor(0xFFFFEA00); pCombo.setAlpha((int)(255 * a));
        c.drawText(label, VIRT_W / 2f, VIRT_H / 2f, pCombo);

        // Sub-label
        pCombo.setColor(0xFFFFFFFF); pCombo.setAlpha((int)(200 * a));
        pCombo.setTextSize(10f);
        c.drawText(multi, VIRT_W / 2f, VIRT_H / 2f + 16, pCombo);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Shuffle message
    // ════════════════════════════════════════════════════════════════════════

    private void drawShuffleMsg(Canvas c) {
        float prog = (float) engine.shuffleMsgTick / GameEngine.SHUFFLE_MSG_TICKS;
        float a    = prog > 0.8f ? (1f - prog) / 0.2f
                   : prog < 0.2f ? prog / 0.2f : 1f;

        // Background pill
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF000000); bg.setAlpha((int)(180 * a));
        tmpRect.set(VIRT_W / 2f - 72, VIRT_H / 2f - 14, VIRT_W / 2f + 72, VIRT_H / 2f + 12);
        c.drawRoundRect(tmpRect, 12, 12, bg);

        pShuffleMsg.setColor(0xFF00E5FF);
        pShuffleMsg.setAlpha((int)(255 * a));
        pShuffleMsg.setTextSize(14f);
        c.drawText("洗牌中...", VIRT_W / 2f, VIRT_H / 2f + 5, pShuffleMsg);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Title screen
    // ════════════════════════════════════════════════════════════════════════

    private void drawTitle(Canvas c) {
        // Gradient background
        for (int i = 0; i < VIRT_H; i++) {
            pLine.setColor(Color.rgb(26 + i / 12, 35 + i / 10, 100 + i / 5));
            c.drawLine(0, i, VIRT_W, i, pLine);
        }
        // Decorative pieces
        int[] demo = {Piece.HEART, Piece.STAR, Piece.LEAF, Piece.DROP, Piece.GEM, Piece.CANDY, Piece.HEART, Piece.STAR};
        for (int i = 0; i < 8; i++) {
            drawPiece(c, demo[i],              20 + i * 40, 40,          13, 0.30f);
            drawPiece(c, demo[(i + 3) % 6 + 1], 20 + i * 40, VIRT_H - 40, 13, 0.30f);
        }

        pTextBig.setColor(0xFFFFEA00); pTextBig.setTextSize(26f);
        c.drawText("可爱消消乐", VIRT_W / 2f, 90, pTextBig);

        pTextBig.setColor(0xFFFFFFFF); pTextBig.setTextSize(14f);
        c.drawText("Kawaii Match", VIRT_W / 2f, 110, pTextBig);

        pTextSub.setColor(0xFFB0BEC5); pTextSub.setTextSize(10f);
        c.drawText("20 Levels  ·  Match 3+  ·  Chain for big scores!", VIRT_W / 2f, 132, pTextSub);

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(12f);
            c.drawText("Press  START  or  A  to play", VIRT_W / 2f, 170, pTextSub);
        }

        pTextSub.setColor(0xFF78909C); pTextSub.setTextSize(9f);
        c.drawText("D-pad: move  ·  A: select  ·  direction: swap  ·  B: cancel", VIRT_W / 2f, 200, pTextSub);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Overlay screens
    // ════════════════════════════════════════════════════════════════════════

    private void drawWin(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);
        drawStarRow(c, VIRT_W / 2f, 50, computeStars());

        pTextBig.setColor(0xFFFFEA00); pTextBig.setTextSize(22f);
        c.drawText("LEVEL  CLEAR!", VIRT_W / 2f, 95, pTextBig);

        pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(12f);
        c.drawText("Score: " + engine.score + "  /  " + engine.levelCfg.targetScore, VIRT_W / 2f, 116, pTextSub);

        // High score
        int hi = engine.highScores[engine.currentLevel];
        if (hi > engine.levelCfg.targetScore) {
            pTextSub.setColor(0xFFFFD740); pTextSub.setTextSize(10f);
            c.drawText("Best: " + hi, VIRT_W / 2f, 132, pTextSub);
        }

        if (!engine.isLastLevel()) {
            pTextSub.setColor(0xFFB2EBF2); pTextSub.setTextSize(11f);
            c.drawText("Level " + (engine.currentLevel + 2) + "  unlocked!", VIRT_W / 2f, 148, pTextSub);
        }

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(11f);
            c.drawText("Press A to continue", VIRT_W / 2f, 170, pTextSub);
        }
    }

    private void drawGameOver(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);

        pTextBig.setColor(0xFFFF5252); pTextBig.setTextSize(24f);
        c.drawText("GAME  OVER", VIRT_W / 2f, 90, pTextBig);

        pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(11f);
        c.drawText("Score: " + engine.score + "  ·  Target: " + engine.levelCfg.targetScore, VIRT_W / 2f, 115, pTextSub);

        int gap = engine.levelCfg.targetScore - engine.score;
        pTextSub.setColor(0xFFFFCDD2); pTextSub.setTextSize(10f);
        c.drawText("Just " + gap + " more points needed – try again!", VIRT_W / 2f, 133, pTextSub);

        // Best score this session
        int hi = engine.highScores[engine.currentLevel];
        if (hi > 0) {
            pTextSub.setColor(0xFFFFD740); pTextSub.setTextSize(10f);
            c.drawText("Your best: " + hi, VIRT_W / 2f, 150, pTextSub);
        }

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(11f);
            c.drawText("Press A to retry", VIRT_W / 2f, 170, pTextSub);
        }
    }

    private void drawAllClear(Canvas c) {
        c.drawRect(0, 0, VIRT_W, VIRT_H, pOverlay);
        drawStarRow(c, VIRT_W / 2f, 48, 3);

        pTextBig.setColor(0xFFFFEA00); pTextBig.setTextSize(20f);
        c.drawText("ALL  LEVELS  CLEAR!", VIRT_W / 2f, 90, pTextBig);

        pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(12f);
        c.drawText("You are a Kawaii Master!", VIRT_W / 2f, 113, pTextSub);

        pTextSub.setColor(0xFFB2EBF2); pTextSub.setTextSize(10f);
        c.drawText("Final Score: " + engine.score, VIRT_W / 2f, 132, pTextSub);

        long t = System.currentTimeMillis();
        if ((t / 500) % 2 == 0) {
            pTextSub.setColor(0xFFFFFFFF); pTextSub.setTextSize(11f);
            c.drawText("Press A to return to title", VIRT_W / 2f, 168, pTextSub);
        }
    }

    private void drawPaused(Canvas c) {
        Paint ov = new Paint(); ov.setColor(0x88000000);
        c.drawRect(0, 0, VIRT_W, VIRT_H, ov);

        pTextBig.setColor(0xFFFFFFFF); pTextBig.setTextSize(26f);
        c.drawText("PAUSED", VIRT_W / 2f, VIRT_H / 2f - 10, pTextBig);

        pTextSub.setColor(0xFFAABBCC); pTextSub.setTextSize(11f);
        c.drawText("Press START to resume", VIRT_W / 2f, VIRT_H / 2f + 16, pTextSub);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════════

    private int computeStars() {
        float r = (float) engine.score / engine.levelCfg.targetScore;
        return r >= 1.8f ? 3 : r >= 1.3f ? 2 : 1;
    }

    private void drawStarRow(Canvas c, float cx, float top, int filled) {
        for (int i = 0; i < 3; i++) {
            pStar.setColor(i < filled ? 0xFFFFD740 : 0xFF555566);
            drawStarShape(c, cx - 30 + i * 30, top, 11, pStar);
        }
    }

    private void drawStarShape(Canvas c, float cx, float cy, float r, Paint p) {
        tmpPath.reset();
        float inner = r * 0.42f;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0 - Math.PI / 2.0;
            float  rad = (i % 2 == 0) ? r : inner;
            float  px = cx + (float)(rad * Math.cos(a));
            float  py = cy + (float)(rad * Math.sin(a));
            if (i == 0) tmpPath.moveTo(px, py); else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        c.drawPath(tmpPath, p);
    }
}
