package com.fleetacademy.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

class MenuView extends View {

    interface Host {
        void onContinue();

        void onNewGame();

        void onBadges();

        void onGalaxy();
    }

    private final Host host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[] scenes = new Bitmap[12];
    private final boolean hasSave;
    private final int doneCount;
    private float w, h;
    private float press = -1;

    MenuView(Context ctx, Host host, boolean hasSave, int doneCount) {
        super(ctx);
        this.host = host;
        this.hasSave = hasSave;
        this.doneCount = doneCount;
        setContentDescription("Linux Fleet Academy main menu");
    }

    private void box(Canvas c, int color, float l, float t, float r, float b, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(l, t, r, b, rad, rad, p);
    }

    private void stroke(Canvas c, int color, float width, float l, float t, float r, float b, float rad) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawRoundRect(l, t, r, b, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, x, y, p);
    }

    private void textC(Canvas c, String s, float cx, float y, float size, int color, boolean bold) {
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setColor(color);
        c.drawText(s, cx - p.measureText(s) / 2f, y, p);
    }

    private RectF btn(int i) {
        float top = h * 0.60f + i * h * 0.085f;
        return new RectF(w * 0.12f, top, w * 0.88f, top + h * 0.062f);
    }

    private int buttons() {
        return hasSave ? 4 : 3;
    }

    private String label(int i) {
        if (hasSave) {
            return new String[]{"CONTINUE MISSION", "NEW GAME", "BADGE COLLECTION", "GALAXY MAP"}[i];
        }
        return new String[]{"START MISSION", "BADGE COLLECTION", "GALAXY MAP"}[i];
    }

    private int action(int i) {
        if (hasSave) return i;
        return i + 1;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        w = getWidth();
        h = getHeight();
        p.setColor(0xff171219);
        c.drawRect(0, 0, w, h, p);
        for (float y = 0; y < h; y += w * .06f) {
            p.setColor(0xff251c20);
            c.drawRect(0, y, w, y + w * .005f, p);
        }

        text(c, "LINUX FLEET", w * 0.08f, h * 0.14f, w * 0.115f, 0xffffead0, true);
        text(c, "ACADEMY", w * 0.08f, h * 0.205f, w * 0.115f, 0xffe6a55d, true);
        text(c, "BLACKSTAR  /  THE SYSTEMD SHIP", w * 0.085f, h * 0.245f, w * 0.036f, 0xff88cbd2, false);

        ComicView.frame(c, p, w * .075f, h * .28f, w * .925f, h * .52f, w * .006f, 0xff171620);
        ComicView.scene(c, new RectF(w * .095f, h * .29f, w * .905f, h * .51f), 0, scenes);

        if (hasSave) {
            textC(c, doneCount + " OF " + GameData.MISSIONS.size() + " SYSTEMS RESTORED", w / 2f,
                    h * 0.555f, w * 0.036f, 0xff7ef0d0, true);
        } else {
            textC(c, "A CADET. A BROKEN SHIP. A MISSION TO RESTORE IT.", w / 2f,
                    h * 0.555f, w * 0.032f, 0xff7ef0d0, false);
        }

        for (int i = 0; i < buttons(); i++) {
            RectF r = btn(i);
            boolean primary = i == 0;
            boolean down = press == i;
            int fill = primary ? (down ? 0xffa45c35 : 0xff75402b) : (down ? 0xff60412f : 0xff302528);
            ComicView.frame(c, p, r.left, r.top, r.right, r.bottom, Math.max(1, w * .004f), fill);
            textC(c, label(i), r.centerX(), r.centerY() + w * 0.018f, w * 0.042f,
                    primary ? 0xffffefd8 : 0xffeac9a1, true);
        }

        textC(c, "OFFLINE  /  SIMULATED SHELL  /  YOUR PROGRESS STAYS ON THIS DEVICE",
                w / 2f, h * 0.965f, w * 0.026f, 0xff456c82, false);
    }

    private void drawShip(Canvas c, float cx, float cy, float size) {
        float bob = (float) Math.sin(SystemClock.uptimeMillis() / 1400.0) * size * 0.012f;
        cy += bob;
        p.setColor(0x2200e5ff);
        c.drawCircle(cx, cy, size * 0.36f, p);

        Path hull = new Path();
        hull.moveTo(cx, cy - size * 0.28f);
        hull.lineTo(cx + size * 0.12f, cy - size * 0.12f);
        hull.lineTo(cx + size * 0.34f, cy + size * 0.02f);
        hull.lineTo(cx + size * 0.3f, cy + size * 0.2f);
        hull.lineTo(cx - size * 0.3f, cy + size * 0.2f);
        hull.lineTo(cx - size * 0.34f, cy + size * 0.02f);
        hull.lineTo(cx - size * 0.12f, cy - size * 0.12f);
        hull.close();
        p.setColor(0xff0d1c2e);
        c.drawPath(hull, p);
        stroke(c, 0xff4fd8ff, 2.4f, cx - size * 0.34f, cy - size * 0.28f,
                cx + size * 0.34f, cy + size * 0.2f, size * 0.2f);

        p.setColor(0xff9fe9ff);
        for (int i = 0; i < 4; i++) {
            c.drawRect(cx - size * 0.16f + i * size * 0.09f, cy - size * 0.05f,
                    cx - size * 0.11f + i * size * 0.09f, cy - size * 0.03f, p);
        }
        p.setColor(0xff37e0c8);
        c.drawRect(cx - size * 0.26f, cy + size * 0.22f, cx - size * 0.1f, cy + size * 0.25f, p);
        p.setColor(0xffff7a45);
        c.drawRect(cx + size * 0.04f, cy + size * 0.22f, cx + size * 0.2f, cy + size * 0.25f, p);

        p.setColor(0xffffd166);
        c.drawCircle(cx + size * 0.16f, cy - size * 0.09f, size * 0.024f, p);
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            press = -1;
            for (int i = 0; i < buttons(); i++) {
                RectF r = btn(i);
                if (r.contains(e.getX(), e.getY())) {
                    press = i;
                    invalidate();
                    return true;
                }
            }
        }
        if (action == MotionEvent.ACTION_MOVE && press >= 0 && !btn((int) press).contains(e.getX(), e.getY())) {
            press = -1;
            invalidate();
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            press = -1;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            int pressed = (int) press;
            press = -1;
            invalidate();
            if (pressed >= 0 && btn(pressed).contains(e.getX(), e.getY())) {
                switch (action(pressed)) {
                    case 0:
                        host.onContinue();
                        break;
                    case 1:
                        host.onNewGame();
                        break;
                    case 2:
                        host.onBadges();
                        break;
                    default:
                        host.onGalaxy();
                        break;
                }
            }
            return true;
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        java.util.Arrays.fill(scenes, null);
    }
}
