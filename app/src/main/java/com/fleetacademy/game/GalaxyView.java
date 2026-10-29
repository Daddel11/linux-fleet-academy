package com.fleetacademy.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

class GalaxyView extends View {
    interface Host { void onNode(int index); void onBack(); }
    private final Host host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float w, h;

    GalaxyView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        setContentDescription("Galaxy map. The networkd ship ISV Lumen is online, four ships are locked.");
    }

    private float nodeX(int i) { return w * (0.24f + (i % 2 == 0 ? 0f : 0.5f)); }
    private float nodeY(int i) { return h * (0.20f + i * 0.155f); }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, boolean right) {
        p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, right ? x - p.measureText(s) : x, y, p);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        w = getWidth(); h = getHeight();
        p.setColor(0xff151720); c.drawRect(0, 0, w, h, p);
        float px = Math.max(2, w / 100f);
        java.util.Random rnd = new java.util.Random(2024);
        for (int i = 0; i < 150; i++) {
            p.setColor(i % 4 == 0 ? 0xffc28a57 : 0xff658d96);
            float x = (int) (rnd.nextFloat() * w / px) * px;
            float y = (int) (rnd.nextFloat() * h / px) * px;
            c.drawRect(x, y, x + px, y + px, p);
        }
        text(c, "GALAXY MAP", w * .08f, h * .075f, w * .07f, 0xffffead0, true, false);
        text(c, "DEEP RANGE FLEET  /  ISV LUMEN", w * .085f, h * .105f, w * .032f, 0xffe2ad72, false, false);
        p.setColor(0xff966242);
        for (int i = 1; i < GameData.GALAXY.length; i++) {
            float x1 = nodeX(i - 1), x2 = nodeX(i), y1 = nodeY(i - 1), y2 = nodeY(i);
            float mid = (y1 + y2) / 2;
            c.drawRect(x1 - px / 2, y1, x1 + px / 2, mid, p);
            c.drawRect(Math.min(x1, x2), mid - px / 2, Math.max(x1, x2), mid + px / 2, p);
            c.drawRect(x2 - px / 2, mid, x2 + px / 2, y2, p);
        }
        for (int i = 0; i < GameData.GALAXY.length; i++) {
            float x = nodeX(i), y = nodeY(i), size = Math.min(w * .065f, 27);
            boolean online = i == 0, right = i % 2 != 0;
            ComicView.frame(c, p, x - size, y - size, x + size, y + size, Math.max(1, size / 9), online ? 0xff184c55 : 0xff30292a);
            p.setColor(online ? 0xff8debf0 : 0xff997b69);
            if (online) {
                c.drawRect(x - size * .4f, y - size * .12f, x + size * .4f, y + size * .12f, p);
                c.drawRect(x - size * .15f, y - size * .4f, x + size * .15f, y + size * .35f, p);
            } else {
                c.drawRect(x - size * .32f, y, x + size * .32f, y + size * .35f, p);
                c.drawRect(x - size * .25f, y - size * .3f, x - size * .15f, y, p);
                c.drawRect(x + size * .15f, y - size * .3f, x + size * .25f, y, p);
                c.drawRect(x - size * .25f, y - size * .3f, x + size * .25f, y - size * .2f, p);
            }
            float tx = right ? x - size - 8 : x + size + 8;
            text(c, GameData.GALAXY[i][0], tx, y - 2, w * .038f, online ? 0xffffead0 : 0xffc2ac99, true, right);
            text(c, GameData.GALAXY[i][1], tx, y + 15, w * .028f, online ? 0xff8debf0 : 0xffa48e7d, false, right);
            text(c, GameData.GALAXY[i][2], tx, y + 30, w * .026f, online ? 0xff8debd0 : 0xffb88775, true, right);
        }
        float l = w * .08f, r = w * .42f, t = h - h * .085f, b = h - h * .035f;
        ComicView.frame(c, p, l, t, r, b, Math.max(1, w * .004f), 0xff302528);
        String back = "< BACK";
        p.setTextSize(w * .038f); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text(c, back, (l + r - p.measureText(back)) / 2, (t + b) / 2 + w * .014f, w * .038f, 0xffffe0b6, true, false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        if (e.getY() > h - h * .1f && e.getX() < w * .5f) { host.onBack(); return true; }
        for (int i = 0; i < GameData.GALAXY.length; i++) {
            if (Math.hypot(e.getX() - nodeX(i), e.getY() - nodeY(i)) < 55) { host.onNode(i); return true; }
        }
        return true;
    }
}
