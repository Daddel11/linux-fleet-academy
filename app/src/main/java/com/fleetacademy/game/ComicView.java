package com.fleetacademy.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

class ComicView extends View {
    interface Done { void done(); }

    private final GameData.Comic comic;
    private final Done onDone;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[] scenes = new Bitmap[12];
    private int index;
    private float scale = 1f, scroll, touchY, downY, maxScroll;
    private boolean moved;

    ComicView(Context ctx, GameData.Comic comic, Done onDone) {
        super(ctx);
        this.comic = comic;
        this.onDone = onDone;
        setBackgroundColor(0xff120e13);
        setContentDescription("Comic scene. Tap to continue.");
    }

    static void frame(Canvas c, Paint p, float l, float t, float r, float b, float unit, int fill) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xff160e10); c.drawRect(l, t, r, b, p);
        p.setColor(0xff75402b); c.drawRect(l + unit, t + unit, r - unit, b - unit, p);
        p.setColor(0xffd19654); c.drawRect(l + 2 * unit, t + 2 * unit, r - 2 * unit, b - 2 * unit, p);
        p.setColor(fill); c.drawRect(l + 3 * unit, t + 3 * unit, r - 3 * unit, b - 3 * unit, p);
        p.setColor(0xffffd58b);
        c.drawRect(l + unit, t + unit, l + 3 * unit, t + 3 * unit, p);
        c.drawRect(r - 3 * unit, t + unit, r - unit, t + 3 * unit, p);
        c.drawRect(l + unit, b - 3 * unit, l + 3 * unit, b - unit, p);
        c.drawRect(r - 3 * unit, b - 3 * unit, r - unit, b - unit, p);
    }

    private static void rect(Canvas c, Paint p, int color, int x, int y, int w, int h) {
        p.setColor(color); c.drawRect(x, y, x + w, y + h, p);
    }

    private static void sprite(Canvas c, Paint p, String[] rows, int x, int y, int pixel, int[] colors) {
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                int n = Character.digit(rows[row].charAt(col), 16);
                if (n > 0 && n < colors.length) rect(c, p, colors[n], x + col * pixel, y + row * pixel, pixel, pixel);
            }
        }
    }

    static void scene(Canvas canvas, RectF dst, int scene, Bitmap[] scenes) {
        int key = Math.floorMod(scene, 4) * 3 + (scene == 0 ? 0 : scene % 3 == 0 ? 1 : 2);
        Bitmap bitmap = scenes[key];
        if (bitmap == null) {
            bitmap = createScene(scene);
            scenes[key] = bitmap;
        }
        Paint pixels = new Paint();
        pixels.setFilterBitmap(false);
        canvas.drawBitmap(bitmap, null, dst, pixels);
    }

    private static Bitmap createScene(int scene) {
        Bitmap bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint q = new Paint();
        rect(c, q, 0xff171620, 0, 0, 96, 48);
        rect(c, q, 0xff29303a, 0, 5, 96, 28);
        for (int x = 0; x < 96; x += 16) {
            rect(c, q, 0xff50362c, x, 6, 2, 27);
            rect(c, q, 0xffb16b3d, x + 2, 9, 12, 2);
            rect(c, q, 0xff78513a, x + 4, 12, 8, 11);
            rect(c, q, 0xff293b42, x + 5, 13, 6, 9);
        }
        rect(c, q, 0xff16171c, 0, 32, 96, 16);
        rect(c, q, 0xff8f5334, 0, 32, 96, 2);
        for (int x = 0; x < 96; x += 12) rect(c, q, 0xff4b342b, x, 39, 9, 1);
        rect(c, q, 0xffc48149, 3, 2, 90, 2);
        rect(c, q, 0xff70412e, 6, 4, 3, 19);
        rect(c, q, 0xffcb8a4e, 7, 4, 1, 19);
        rect(c, q, 0xff573929, 68, 24, 24, 9);
        rect(c, q, 0xffbd7b43, 70, 25, 20, 2);
        for (int x = 72; x < 89; x += 4) rect(c, q, 0xffe8b66e, x, 28, 2, 2);
        int[] linus = {0,0xff261c1b,0xff513328,0xffd69a6d,0xffeeb37a,0xffc64c35,0xff7f2926,0xffffa53c,0xffffd077,0xff382c2a,0xff8a704f};
        String[] person = {"000222220000","002222222000","022333333200","023777777320","023787787320","023333333320","002344443200","000233332000","002555555200","025555555520","025655556520","025655556520","025555555520","002569965200","002990099200","002990099200","022990099220"};
        int[] nova = {0,0xff15455a,0xff257b91,0xff54cbe2,0xffb8f8ff,0xff398fa5};
        String[] hologram = {"00022222000","00233333200","02344444320","02344444320","02343434320","02344444320","00234443200","00023332000","00233333200","02333333320","02323532320","00223332200","00025352000","00002520000"};
        int shift = Math.floorMod(scene, 4) * 3;
        sprite(c, q, person, 17 + shift, 10, 2, linus);
        sprite(c, q, hologram, 57 - shift / 2, 8, 2, nova);
        rect(c, q, 0xff54cbe2, 57 - shift / 2, 38, 22, 1);
        rect(c, q, 0xff28798a, 60 - shift / 2, 40, 16, 1);
        if (scene == 0) {
            rect(c, q, 0xffe3a45f, 37, 7, 20, 2);
            rect(c, q, 0xff72d6e6, 41, 10, 12, 3);
        } else if (scene % 3 == 0) {
            rect(c, q, 0xffd78b4d, 36, 20, 15, 2);
            rect(c, q, 0xff69412d, 39, 22, 9, 7);
        } else {
            rect(c, q, 0xff4eb8cf, 38, 15, 13, 1);
            rect(c, q, 0xff4eb8cf, 41, 18, 8, 1);
        }
        return bitmap;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        java.util.Arrays.fill(scenes, null);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        p.setColor(color); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, x, y, p);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        scale = getWidth() / 400f;
        if (scale <= 0 || comic.panels.isEmpty()) return;
        float h = getHeight() / scale;
        c.save(); c.scale(scale, scale);
        p.setColor(0xff17121a); c.drawRect(0, 0, 400, h, p);
        for (int y = 0; y < h; y += 24) {
            p.setColor(0xff241b20); c.drawRect(0, y, 400, y + 2, p);
        }
        float header = h * .075f, top = header + h * .02f, bottom = h - h * .045f;
        text(c, "BLACKSTAR  /  MISSION LOG", 18, header, 16, 0xffe9b770, true);
        text(c, (index + 1) + " / " + comic.panels.size(), 341, header, 13, 0xff88cbd2, false);
        frame(c, p, 18, top, 382, bottom, 2, 0xff211a1a);
        float artH = Math.max(45, (bottom - top) * .42f);
        RectF art = new RectF(26, top + 8, 374, top + artH);
        scene(c, art, comic.panels.get(index).art, scenes);
        GameData.Panel panel = comic.panels.get(index);
        text(c, panel.who, 36, top + artH + 22, 14, 0xffffc77e, true);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(14.2f); paint.setColor(0xffffead0);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        StaticLayout layout = new StaticLayout(panel.text, paint, 328, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0, false);
        float textTop = top + artH + 30, textBottom = Math.max(textTop + 1, bottom - 14);
        maxScroll = Math.max(0, layout.getHeight() - (textBottom - textTop));
        scroll = Math.min(scroll, maxScroll);
        c.save(); c.clipRect(36, textTop, 364, textBottom); c.translate(36, textTop - scroll); layout.draw(c); c.restore();
        String hint = maxScroll > 0 ? "SWIPE TO READ  /  TAP TO CONTINUE" : "TAP TO CONTINUE";
        p.setTextSize(11.2f); p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text(c, hint, 200 - p.measureText(hint) / 2, h - h * .018f, 11.2f, 0xffb69378, false);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        float y = e.getY() / scale;
        if (action == MotionEvent.ACTION_DOWN) { touchY = downY = y; moved = false; }
        else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(y - downY) > 5) moved = true;
            scroll = Math.max(0, Math.min(maxScroll, scroll + touchY - y));
            touchY = y; invalidate();
        } else if (action == MotionEvent.ACTION_UP && !moved && !comic.panels.isEmpty()) {
            if (index < comic.panels.size() - 1) { index++; scroll = 0; invalidate(); }
            else if (onDone != null) onDone.done();
        }
        return true;
    }
}
