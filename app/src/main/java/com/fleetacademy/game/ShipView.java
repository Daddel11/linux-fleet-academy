package com.fleetacademy.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class ShipView extends View {

    interface Host {
        boolean isDone(String mission);

        void talk(GameData.N npc);

        void openMission(GameData.M mission);

        void pauseMenu();

        void badges();
    }

    private static final float VIEW_W = 400f;
    private static final float SPEED = 190f;
    private static final float RADIUS = 15f;

    private final Host host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Float> doorAnim = new HashMap<>();
    private final Paint pixelPaint = new Paint();
    private Bitmap worldBitmap;

    private float px, py, vx, vy, walk, scale = 1f, logicalH = 800f;
    private float camX, camY;
    private long last = 0;
    private String facing = "down";

    private float joyX, joyY;
    private boolean joyActive;
    private int joyPointerId = -1;
    private float joyDX, joyDY;

    private GameData.N bubbleNpc;
    private String bubbleText;
    private String bubbleAction;
    private float bubbleWorldX, bubbleWorldY;
    private float bubbleScroll, bubbleTouchY;
    private boolean bubbleDragging, bubbleMoved;

    private String roomCard;
    private long roomCardAt;
    private String currentRoom = "";

    private int starsSeed = 1337;

    ShipView(Context ctx, Host host, float startX, float startY) {
        super(ctx);
        this.host = host;
        px = startX;
        py = startY;
        if (blocked(px, py)) {
            px = 900;
            py = 4900;
        }
        camX = px;
        camY = py;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setContentDescription("Fleet Academy ship deck. Drag the left pad to walk, tap action near crew to talk.");
    }

    void place(float x, float y) {
        px = x;
        py = y;
        camX = x;
        camY = y;
        if (blocked(px, py)) {
            px = 900;
            py = 4900;
            camX = px;
            camY = py;
        }
        invalidate();
    }

    void say(GameData.N npc, String text, String action) {
        bubbleScroll = 0;
        bubbleNpc = npc;
        bubbleText = text;
        bubbleAction = action;
        bubbleWorldX = npc == null ? px : npc.x;
        bubbleWorldY = npc == null ? py : npc.y - 60;
        invalidate();
    }

    void clearBubble() {
        bubbleNpc = null;
        bubbleText = null;
        bubbleAction = null;
    }

    float x() { return px; }
    float y() { return py; }

    /* --------------------------------------------------------- collision */

    private static boolean inRect(float x, float y, float x1, float y1, float x2, float y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private boolean gateOpen(GameData.D d) {
        for (String need : d.needs) if (!host.isDone(need)) return false;
        return true;
    }

    private float doorLeft(GameData.D d) {
        return d.gate && d.y1 <= GameData.SPINE_Y2 && d.y2 >= GameData.SPINE_Y2
                ? Math.min(d.x1, GameData.SPINE_X1) : d.x1;
    }

    private float doorRight(GameData.D d) {
        return d.gate && d.y1 <= GameData.SPINE_Y2 && d.y2 >= GameData.SPINE_Y2
                ? Math.max(d.x2, GameData.SPINE_X2) : d.x2;
    }

    private boolean walkable(float x, float y) {
        if (x < 40 || y < 40 || x > GameData.WORLD_W - 40 || y > GameData.WORLD_H - 40) return false;
        for (GameData.D d : GameData.DOORS) {
            if (d.gate && !gateOpen(d) && inRect(x, y, doorLeft(d) - 6, d.y1 - 6, doorRight(d) + 6, d.y2 + 6)) return false;
        }
        if (inRect(x, y, GameData.SPINE_X1, GameData.SPINE_Y1, GameData.SPINE_X2, GameData.SPINE_Y2)) return true;
        if (inRect(x, y, GameData.SPINE_X1, GameData.LINK_Y1 - 16, GameData.SPINE_X2, GameData.LINK_Y2 + 16)) return true;
        for (GameData.R r : GameData.ROOMS) if (inRect(x, y, r.x1 + 8, r.y1 + 8, r.x2 - 8, r.y2 - 8)) return true;
        for (GameData.D d : GameData.DOORS) if (inRect(x, y, d.x1, d.y1, d.x2, d.y2)) return true;
        return false;
    }

    private boolean blocked(float x, float y) {
        if (!walkable(x, y)) return true;
        for (float ox : new float[]{-RADIUS, RADIUS}) {
            for (float oy : new float[]{-RADIUS, RADIUS}) if (!walkable(x + ox, y + oy)) return true;
        }
        for (GameData.N n : GameData.NPCS) if (Math.hypot(x - n.x, y - n.y) < 24) return true;
        for (GameData.R r : GameData.ROOMS) {
            for (GameData.P pr : GameData.props(r.id)) {
                if (pr.type == GameData.PLANT) continue;
                if (inRect(x, y, pr.x - 6, footprintTop(pr) - 6, pr.x + pr.w + 6, pr.y + pr.h + 6)) return true;
            }
        }
        return false;
    }

    private static float footprintTop(GameData.P pr) {
        switch (pr.type) {
            case GameData.BED:
            case GameData.TABLE:
            case GameData.REACTOR:
            case GameData.DISH:
            case GameData.VALVE:
                return pr.y;
            default:
                return pr.y + pr.h * 0.65f;
        }
    }

    /* ------------------------------------------------------------- input */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() / scale;
        float y = e.getY() / scale;
        int action = e.getActionMasked();
        float joyCX = 86, joyCY = logicalH - 118, actionCX = 330, actionCY = logicalH - 118;

        if (action == MotionEvent.ACTION_DOWN) {
            if (bubbleText != null && insideBubble(x, y)) {
                bubbleDragging = true;
                bubbleMoved = false;
                bubbleTouchY = y;
                return true;
            }
            if (x < 62 && y < 62) {
                host.pauseMenu();
                return true;
            }
            if (x > 286 && x < 396 && y > 6 && y < 52) {
                host.badges();
                return true;
            }
            if (Math.hypot(x - actionCX, y - actionCY) < 54) {
                interact();
                return true;
            }
            if (Math.hypot(x - joyCX, y - joyCY) < 130) {
                joyActive = true;
                joyPointerId = e.getPointerId(0);
                updateJoy(x - joyCX, y - joyCY);
                return true;
            }
        }
        if (bubbleDragging) {
            if (action == MotionEvent.ACTION_MOVE) {
                float delta = bubbleTouchY - y;
                if (Math.abs(delta) > 1) bubbleMoved = true;
                RectF r = bubbleRect();
                float max = Math.max(0, bubbleLayout(r.width() - 24).getHeight() - (r.height() - 62));
                bubbleScroll = Math.max(0, Math.min(max, bubbleScroll + delta));
                bubbleTouchY = y;
                invalidate();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                bubbleDragging = false;
                if (action == MotionEvent.ACTION_UP && !bubbleMoved && insideBubble(x, y)) {
                    RectF r = bubbleRect();
                    if (y >= r.bottom - 30) {
                        if (bubbleAction != null && bubbleNpc != null) {
                            GameData.M m = GameData.mission(bubbleNpc.mission);
                            clearBubble();
                            if (m != null) host.openMission(m);
                        } else clearBubble();
                    }
                }
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && joyActive) {
            int pointer = e.findPointerIndex(joyPointerId);
            if (pointer >= 0) updateJoy(e.getX(pointer) / scale - joyCX, e.getY(pointer) / scale - joyCY);
            else resetJoy();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL ||
                ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                        && e.getPointerId(e.getActionIndex()) == joyPointerId)) {
            resetJoy();
            return true;
        }
        return true;
    }

    private void resetJoy() {
        joyActive = false;
        joyPointerId = -1;
        joyDX = joyDY = joyX = joyY = 0;
        invalidate();
    }

    private void updateJoy(float dx, float dy) {
        joyX = dx;
        joyY = dy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 12) {
            joyDX = joyDY = 0;
            return;
        }
        float f = Math.min(1f, len / 62f);
        joyDX = dx / len * f;
        joyDY = dy / len * f;
    }

    private boolean insideBubble(float x, float y) {
        RectF r = bubbleRect();
        return r != null && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
    }

    private RectF bubbleRect() {
        if (bubbleText == null) return null;
        float sx = bubbleWorldX - camX + VIEW_W / 2f;
        float sy = bubbleWorldY - camY + logicalH / 2f;
        float w = 300;
        float h = Math.min(Math.max(96, bubbleLayout(w - 24).getHeight() + 62), Math.max(96, logicalH - 140));
        float left = Math.max(10, Math.min(VIEW_W - w - 10, sx - w / 2));
        float top = Math.max(58, Math.min(logicalH - h - 20, sy - h));
        return new RectF(left, top, left + w, top + h);
    }

    private void interact() {
        GameData.N near = null;
        float best = 100;
        for (GameData.N n : GameData.NPCS) {
            float d = (float) Math.hypot(px - n.x, py - n.y);
            if (d < best) {
                best = d;
                near = n;
            }
        }
        if (near != null) {
            host.talk(near);
            return;
        }
        for (GameData.R r : GameData.ROOMS) {
            if (!inRect(px, py, r.x1, r.y1, r.x2, r.y2)) continue;
            for (GameData.P pr : GameData.props(r.id)) {
                if (pr.type != GameData.CONSOLE) continue;
                if (Math.hypot(px - (pr.x + pr.w / 2f), py - (pr.y + pr.h / 2f)) < 95) {
                    GameData.M m = GameData.mission(r.id);
                    if (m != null) {
                        host.openMission(m);
                        return;
                    }
                }
            }
        }
        say(null, "No one in reach. Walk up to a crew member or a glowing console, then tap ACTION.",
                null);
    }

    /* ------------------------------------------------------------ render */

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
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setColor(color);
        c.drawText(s, cx - p.measureText(s) / 2f, y, p);
    }

    private void neonStroke(Canvas c, int color, float width, float l, float t, float r, float b, float rad) {
        int a = (color >>> 24) & 0xff;
        int glowColor = (color & 0x00ffffff) | (Math.min(90, a / 3) << 24);
        stroke(c, glowColor, width * 3.2f, l, t, r, b, rad);
        stroke(c, color, width, l, t, r, b, rad);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min((now - last) / 1000f, 0.05f);
        last = now;

        scale = getWidth() / VIEW_W;
        if (scale <= 0) return;
        logicalH = getHeight() / scale;

        if (Math.abs(joyDX) > 0.01f || Math.abs(joyDY) > 0.01f) {
            float nx = px + joyDX * SPEED * dt;
            float ny = py + joyDY * SPEED * dt;
            if (!blocked(nx, py)) px = nx;
            if (!blocked(px, ny)) py = ny;
            if (Math.abs(joyDX) > Math.abs(joyDY)) facing = joyDX > 0 ? "right" : "left";
            else facing = joyDY > 0 ? "down" : "up";
            walk += dt * 11f;
            if (bubbleText != null && bubbleNpc == null) clearBubble();
        }

        boolean moving = Math.abs(joyDX) > 0.01f || Math.abs(joyDY) > 0.01f;
        for (GameData.D d : GameData.DOORS) {
            float target = (!d.gate || gateOpen(d)) ? 1f : 0f;
            Float cur = doorAnim.get(d.label);
            float v = cur == null ? target : cur;
            v += (target - v) * Math.min(1f, dt * 6f);
            doorAnim.put(d.label, v);
        }

        camX += (px - camX) * Math.min(1f, dt * 6f);
        camY += (py - camY) * Math.min(1f, dt * 6f);
        camX = Math.max(VIEW_W / 2f, Math.min(GameData.WORLD_W - VIEW_W / 2f, camX));
        camY = Math.max(logicalH / 2f, Math.min(GameData.WORLD_H - logicalH / 2f, camY));

        c.drawColor(0xff04070f);
        c.save();
        c.scale(scale, scale);
        int bitmapWidth = (int) (VIEW_W / 2);
        int bitmapHeight = Math.max(1, (int) Math.ceil(logicalH / 2));
        if (worldBitmap == null || worldBitmap.getWidth() != bitmapWidth || worldBitmap.getHeight() != bitmapHeight) {
            if (worldBitmap != null) worldBitmap.recycle();
            worldBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        }
        Canvas world = new Canvas(worldBitmap);
        world.scale(0.5f, 0.5f);
        p.setAntiAlias(false);
        drawSpace(world);
        world.save();
        world.translate(Math.round((VIEW_W / 2f - camX) / 2f) * 2f,
                Math.round((logicalH / 2f - camY) / 2f) * 2f);
        drawHull(world);
        drawRooms(world);
        drawDoors(world);
        drawDepthSorted(world, moving);
        world.restore();
        pixelPaint.setFilterBitmap(false);
        c.drawBitmap(worldBitmap, null, new RectF(0, 0, VIEW_W, bitmapHeight * 2f), pixelPaint);
        p.setAntiAlias(true);

        drawBubble(c);
        drawHud(c, moving);
        c.restore();
        trackRoom(now);

        postInvalidateOnAnimation();
    }

    private void drawSpace(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, logicalH, new int[]{0xff050a16, 0xff081426, 0xff050a16},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, VIEW_W, logicalH, p);
        p.setShader(null);
        java.util.Random rnd = new java.util.Random(starsSeed);
        for (int i = 0; i < 130; i++) {
            float sx = rnd.nextFloat() * VIEW_W;
            float sy = rnd.nextFloat() * logicalH;
            float size = rnd.nextFloat() * 1.6f + 0.4f;
            int alpha = (int) (60 + rnd.nextFloat() * 150);
            p.setColor((alpha << 24) | 0xbfe9ff);
            c.drawCircle(sx, sy, size, p);
        }
        p.setColor(0x142f6cff);
        c.drawCircle(VIEW_W * 0.2f, logicalH * 0.18f, 120, p);
        p.setColor(0x12a03cff);
        c.drawCircle(VIEW_W * 0.85f, logicalH * 0.72f, 150, p);
    }

    private void drawHull(Canvas c) {
        Path path = new Path();
        path.addRoundRect(new RectF(120, 300, 1680, 5000), 180, 180, Path.Direction.CW);
        path.addRoundRect(new RectF(560, 130, 1240, 420), 120, 120, Path.Direction.CW);
        p.setColor(0xff0a1220);
        c.drawPath(path, p);
        stroke(c, 0xff1c3550, 14, 120, 300, 1680, 5000, 180);
        stroke(c, 0x3a5fe0ff, 3, 120, 300, 1680, 5000, 180);
        stroke(c, 0x3a5fe0ff, 3, 560, 130, 1240, 420, 120);

        for (int y = 600; y < 4900; y += 260) {
            p.setColor(0x14ffffff);
            p.setStrokeWidth(2);
            c.drawLine(130, y, 1680, y, p);
        }
        p.setColor(0x2200e5ff);
        c.drawRect(GameData.SPINE_X1 + 20, GameData.SPINE_Y1, GameData.SPINE_X2 - 20, GameData.SPINE_Y2, p);
    }

    private void drawRooms(Canvas c) {
        for (GameData.R r : GameData.ROOMS) {
            if (r.x2 < camX - VIEW_W || r.x1 > camX + VIEW_W || r.y2 < camY - logicalH
                    || r.y1 > camY + logicalH) continue;

            p.setColor(0xff332a27);
            c.drawRect(r.x1, r.y1, r.x2, r.y2, p);
            for (float gy = r.y1 + 32; gy < r.y2; gy += 40) {
                pixelRect(c, 0xff211d1d, r.x1, gy, r.x2, gy + 2);
                float offset = ((int) ((gy - r.y1) / 40) % 2) * 32;
                for (float gx = r.x1 + offset; gx < r.x2; gx += 64) {
                    pixelRect(c, 0xff211d1d, gx, gy, gx + 2, Math.min(gy + 40, r.y2));
                    pixelRect(c, 0xff4b3930, gx + 4, gy + 4, Math.min(gx + 28, r.x2), gy + 6);
                    pixelRect(c, 0xff886443, gx + 5, gy + 8, gx + 7, gy + 10);
                }
            }
            pixelRect(c, 0xff171b20, r.x1, r.y1, r.x2, r.y1 + 28);
            pixelRect(c, 0xff644332, r.x1, r.y1 + 4, r.x2, r.y1 + 10);
            pixelRect(c, 0xffbb8050, r.x1, r.y1 + 4, r.x2, r.y1 + 6);
            pixelRect(c, 0xff805639, r.x1, r.y1 + 20, r.x2, r.y1 + 26);
            for (float gx = r.x1 + 16; gx < r.x2 - 8; gx += 48) {
                pixelRect(c, 0xffd2a365, gx, r.y1 + 12, gx + 4, r.y1 + 16);
                pixelRect(c, 0xff30231e, gx + 12, r.y1 + 2, gx + 18, r.y1 + 12);
            }
            stroke(c, 0xff9b7048, 4f, r.x1, r.y1, r.x2, r.y2, 0);
            textC(c, r.name, (r.x1 + r.x2) / 2f, r.y1 + 40, 12, 0xffe3c291, true);
        }
    }

    private void drawDoors(Canvas c) {
        for (GameData.D d : GameData.DOORS) {
            Float av = doorAnim.get(d.label);
            float open = av == null ? 1f : av;
            float left = doorLeft(d), right = doorRight(d);
            boolean horizontal = (right - left) >= (d.y2 - d.y1);
            int color = d.gate ? 0xff5fd0ff : 0xff2f7f9a;
            int edge = d.gate ? 0xffa9ecff : 0xff59b6c9;

            p.setColor(0xff0a1524);
            c.drawRect(left, d.y1, right, d.y2, p);

            if (horizontal) {
                float midX = (left + right) / 2f;
                float gap = (right - left) / 2f * open;
                p.setColor(color);
                c.drawRect(left, d.y1, midX - gap, d.y2, p);
                c.drawRect(midX + gap, d.y1, right, d.y2, p);
                p.setColor(edge);
                c.drawRect(left, d.y1, midX - gap, d.y1 + 3, p);
                c.drawRect(midX + gap, d.y1, right, d.y1 + 3, p);
            } else {
                float midY = (d.y1 + d.y2) / 2f;
                float gap = (d.y2 - d.y1) / 2f * open;
                p.setColor(color);
                c.drawRect(d.x1, d.y1, d.x2, midY - gap, p);
                c.drawRect(d.x1, midY + gap, d.x2, d.y2, p);
            }

            if (d.gate && open < 0.5f) {
                float cx = (left + right) / 2f, cy = (d.y1 + d.y2) / 2f;
                p.setColor(0xffff4d6d);
                c.drawCircle(cx, cy, 13, p);
                p.setColor(0xff07131f);
                c.drawRect(cx - 6, cy - 2, cx + 6, cy + 8, p);
                stroke(c, 0xff07131f, 3, cx - 5, cy - 9, cx + 5, cy - 1, 4);
            }
        }
    }

    private static final class DepthItem {
        final float footY;
        final GameData.P prop;
        final GameData.N npc;

        DepthItem(float footY, GameData.P prop, GameData.N npc) {
            this.footY = footY;
            this.prop = prop;
            this.npc = npc;
        }
    }

    private void drawDepthSorted(Canvas c, boolean moving) {
        List<DepthItem> items = new ArrayList<>();
        for (GameData.R r : GameData.ROOMS) {
            if (r.x2 < camX - VIEW_W || r.x1 > camX + VIEW_W || r.y2 < camY - logicalH
                    || r.y1 > camY + logicalH) continue;
            for (GameData.P prop : GameData.props(r.id)) {
                items.add(new DepthItem(prop.y + prop.h, prop, null));
            }
        }
        for (GameData.N npc : GameData.NPCS) {
            if (npc.x >= camX - VIEW_W && npc.x <= camX + VIEW_W && npc.y >= camY - logicalH
                    && npc.y <= camY + logicalH) items.add(new DepthItem(npc.y, null, npc));
        }
        items.add(new DepthItem(py, null, null));
        Collections.sort(items, new Comparator<DepthItem>() {
            @Override public int compare(DepthItem a, DepthItem b) {
                return Float.compare(a.footY, b.footY);
            }
        });
        for (DepthItem item : items) {
            if (item.prop != null) drawPixelProp(c, item.prop);
            else if (item.npc != null) drawPixelNpc(c, item.npc);
            else pixelPerson(c, px, py, 0xffa32935, moving, facing, false);
        }
    }

    private void pixelRect(Canvas c, int color, float l, float t, float r, float b) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(Math.round(l / 2f) * 2f, Math.round(t / 2f) * 2f,
                Math.round(r / 2f) * 2f, Math.round(b / 2f) * 2f, p);
    }

    private void drawPixelProp(Canvas c, GameData.P pr) {
        float x = pr.x, y = pr.y, w = pr.w, h = pr.h;
        int accent = pr.color | 0xff000000;
        pixelRect(c, 0x66000000, x + 4, y + h - 4, x + w + 6, y + h + 6);
        c.save();
        c.translate(x, y);
        c.scale(w / 64f, h / 64f);
        if (pr.type == GameData.PLANT) {
            pixelRect(c, 0xff39251f, 16, 44, 48, 64);
            pixelRect(c, 0xffb66d43, 18, 46, 46, 56);
            pixelRect(c, 0xffe3a36b, 14, 42, 50, 46);
            pixelRect(c, 0xff315b3c, 28, 16, 34, 44);
            pixelRect(c, 0xff315b3c, 10, 20, 28, 32);
            pixelRect(c, 0xff3f8050, 36, 10, 52, 30);
            pixelRect(c, 0xff6ba968, 16, 14, 26, 24);
            pixelRect(c, 0xffa1c87b, 38, 8, 46, 16);
            pixelRect(c, 0xff6ba968, 26, 2, 36, 20);
        } else if (pr.type == GameData.BED || pr.type == GameData.TABLE) {
            pixelRect(c, 0xff251b1b, 0, 4, 64, 64);
            pixelRect(c, 0xff96643f, 4, 2, 60, 54);
            pixelRect(c, 0xffd39b60, 6, 4, 58, 8);
            pixelRect(c, 0xff54372b, 4, 54, 60, 62);
            if (pr.type == GameData.BED) {
                pixelRect(c, 0xffd3c9ad, 8, 10, 56, 50);
                pixelRect(c, 0xfff4e8ce, 12, 10, 52, 22);
                pixelRect(c, 0xff836453, 10, 24, 54, 28);
                pixelRect(c, 0xff456875, 8, 28, 56, 50);
                pixelRect(c, 0xff85a3a0, 8, 28, 56, 32);
                pixelRect(c, 0xffb7b48f, 10, 44, 54, 46);
            } else {
                pixelRect(c, 0xff6b4530, 8, 12, 56, 48);
                pixelRect(c, 0xffbc8855, 10, 16, 54, 18);
                pixelRect(c, 0xffbc8855, 10, 32, 54, 34);
                pixelRect(c, 0xffe4c593, 20, 20, 40, 30);
                pixelRect(c, 0xff49382e, 24, 22, 36, 24);
            }
        } else if (pr.type == GameData.REACTOR || pr.type == GameData.DISH || pr.type == GameData.VALVE) {
            pixelRect(c, 0xff211c1c, 4, 10, 60, 60);
            pixelRect(c, 0xff704a31, 8, 6, 56, 58);
            pixelRect(c, 0xffd19a5a, 16, 2, 48, 8);
            pixelRect(c, 0xffb07a46, 4, 24, 60, 42);
            pixelRect(c, 0xff292c2c, 14, 14, 50, 50);
            pixelRect(c, 0xffd39d5b, 20, 10, 44, 54);
            pixelRect(c, 0xffd39d5b, 10, 20, 54, 44);
            pixelRect(c, 0xff332c29, 18, 18, 46, 46);
            if (pr.type == GameData.REACTOR) {
                pixelRect(c, 0xff9b4a2f, 20, 20, 44, 44);
                pixelRect(c, 0xffffa74f, 24, 18, 40, 46);
                pixelRect(c, 0xffffe3a0, 28, 24, 36, 38);
                pixelRect(c, 0xff6f4634, 8, 14, 14, 50);
                pixelRect(c, 0xff6f4634, 50, 14, 56, 50);
            } else if (pr.type == GameData.VALVE) {
                pixelRect(c, 0xffd38b51, 28, 16, 36, 48);
                pixelRect(c, 0xffd38b51, 16, 28, 48, 36);
                pixelRect(c, 0xffffd18b, 28, 28, 36, 36);
            } else {
                pixelRect(c, 0xff66878a, 22, 22, 42, 42);
                pixelRect(c, 0xffcbe8db, 26, 24, 38, 36);
                pixelRect(c, 0xff704a31, 30, 6, 34, 30);
            }
        } else {
            pixelRect(c, 0xff211d20, 0, 4, 64, 64);
            pixelRect(c, 0xff795139, 4, 2, 60, 58);
            pixelRect(c, 0xffd19a5e, 6, 4, 58, 10);
            pixelRect(c, 0xff4a342b, 4, 52, 60, 60);
            if (pr.type == GameData.CRATE) {
                pixelRect(c, 0xff98633d, 8, 12, 56, 50);
                pixelRect(c, 0xffd09559, 10, 16, 54, 20);
                pixelRect(c, 0xffd09559, 10, 38, 54, 42);
                pixelRect(c, 0xff50372a, 16, 10, 22, 52);
                pixelRect(c, 0xff50372a, 42, 10, 48, 52);
            } else if (pr.type == GameData.TANK) {
                pixelRect(c, 0xff342b29, 10, 10, 54, 52);
                pixelRect(c, 0xff386b6a, 14, 14, 50, 48);
                pixelRect(c, 0xff74b9a7, 18, 18, 24, 42);
                pixelRect(c, 0xffb7e3c5, 26, 28, 42, 32);
                pixelRect(c, 0xffc59156, 8, 10, 56, 14);
                pixelRect(c, 0xffc59156, 8, 46, 56, 50);
            } else if (pr.type == GameData.RACK) {
                for (int i = 0; i < 3; i++) {
                    pixelRect(c, 0xff252a2c, 10, 12 + i * 12, 54, 22 + i * 12);
                    pixelRect(c, 0xffb9824e, 14, 16 + i * 12, 36, 18 + i * 12);
                    pixelRect(c, 0xff80c7a5, 44, 16 + i * 12, 48, 20 + i * 12);
                }
            } else {
                pixelRect(c, 0xff202b2d, 8, 12, 56, 42);
                pixelRect(c, 0xff284c50, 12, 16, 52, 38);
                pixelRect(c, accent, 16, 20, 42, 22);
                pixelRect(c, 0xffa5e6ce, 16, 26, 36, 28);
                pixelRect(c, 0xff69c6bb, 16, 32, 46, 34);
                pixelRect(c, 0xffdca35e, 12, 46, 18, 50);
                pixelRect(c, 0xffdf7960, 22, 46, 28, 50);
                pixelRect(c, 0xff84cdb8, 32, 46, 38, 50);
            }
            pixelRect(c, 0xffe3b578, 6, 54, 10, 58);
            pixelRect(c, 0xffe3b578, 54, 54, 58, 58);
        }
        c.restore();
    }

    private void drawPixelNpc(Canvas c, GameData.N n) {
        GameData.M m = GameData.mission(n.mission);
        boolean done = m != null && host.isDone(m.id);
        boolean nova = n.id.equals("nova") || n.id.equals("nova2");
        pixelPerson(c, n.x, n.y, n.color, false, "down", nova);
        textC(c, n.name, n.x, n.y + 22, 9, 0xffcfe9ff, true);
        pixelRect(c, done ? 0xff42f5a7 : 0xffffd166, n.x + 16, n.y - 55, n.x + 28, n.y - 43);
        textC(c, done ? "v" : "!", n.x + 22, n.y - 45, 10, 0xff04121c, true);
    }

    private void pixelPerson(Canvas c, float x, float y, int coat, boolean moving, String face, boolean hologram) {
        x = Math.round(x / 2f) * 2f;
        y = Math.round(y / 2f) * 2f;
        float step = moving ? ((Math.sin(walk) >= 0) ? 4 : -4) : 0;
        int outline = hologram ? 0xff124c68 : 0xff2c2020;
        int body = hologram ? 0xff35a9d1 : coat | 0xff000000;
        int light = hologram ? 0xffa7f6ff : 0xfff8cf80;
        pixelRect(c, 0x66000000, x - 16, y - 4, x + 16, y + 4);
        pixelRect(c, outline, x - 12, y - 18, x - 2, y + step);
        pixelRect(c, outline, x + 2, y - 18, x + 12, y - step);
        pixelRect(c, hologram ? 0xff69d7ef : 0xff80533d, x - 12, y - 6 + step, x - 2, y - 2 + step);
        pixelRect(c, hologram ? 0xff69d7ef : 0xff80533d, x + 2, y - 6 - step, x + 12, y - 2 - step);
        pixelRect(c, outline, x - 16, y - 38, x + 16, y - 12);
        pixelRect(c, body, x - 14, y - 36, x + 14, y - 14);
        pixelRect(c, hologram ? 0xff69d7ef : 0xffd45b4d, x - 12, y - 36, x + 12, y - 32);
        pixelRect(c, hologram ? 0xff228bb1 : 0xff713235, x - 14, y - 24, x + 14, y - 20);
        pixelRect(c, hologram ? 0xffa2f4ff : 0xffe0ae68, x - 2, y - 26, x + 2, y - 20);
        pixelRect(c, outline, x - 18, y - 32, x - 14, y - 16);
        pixelRect(c, outline, x + 14, y - 32, x + 18, y - 16);
        pixelRect(c, hologram ? 0xff75dcf3 : 0xffd5a17b, x - 16, y - 20, x - 12, y - 14);
        pixelRect(c, hologram ? 0xff75dcf3 : 0xffd5a17b, x + 12, y - 20, x + 16, y - 14);
        pixelRect(c, outline, x - 12, y - 56, x + 12, y - 36);
        pixelRect(c, hologram ? 0xff51c6e6 : 0xffd3a07b, x - 10, y - 52, x + 10, y - 38);
        pixelRect(c, hologram ? 0xffb2f7ff : 0xff473229, x - 12, y - 58, x + 10, y - 50);
        pixelRect(c, hologram ? 0xff76e4f4 : 0xff7a4c33, x - 10, y - 60, x + 6, y - 56);
        if (!face.equals("up")) {
            float shift = face.equals("left") ? -2 : face.equals("right") ? 2 : 0;
            pixelRect(c, hologram ? 0xff155b76 : 0xff8c613e, x - 10 + shift, y - 50, x + 10 + shift, y - 44);
            pixelRect(c, light, x - 8 + shift, y - 49, x - 2 + shift, y - 45);
            pixelRect(c, light, x + 2 + shift, y - 49, x + 8 + shift, y - 45);
            pixelRect(c, outline, x - 2 + shift, y - 48, x + 2 + shift, y - 46);
            pixelRect(c, hologram ? 0xffc9ffff : 0xff704a3b, x + shift, y - 40, x + 4 + shift, y - 38);
        }
        if (hologram) {
            pixelRect(c, 0xff91eeff, x - 12, y - 27, x + 12, y - 25);
            pixelRect(c, 0x994ac8ec, x - 20, y + 2, x + 20, y + 4);
            pixelRect(c, 0xffb9f9ff, x - 10, y + 4, x + 10, y + 6);
        } else {
            pixelRect(c, 0xffe6b878, x - 2, y - 20, x + 2, y - 14);
            pixelRect(c, 0xfff1c37f, x - 8, y - 32, x - 4, y - 28);
            pixelRect(c, 0xfff1c37f, x + 4, y - 32, x + 8, y - 28);
        }
    }

    private void drawProps(Canvas c) {
        for (GameData.R r : GameData.ROOMS) {
            if (r.x2 < camX - VIEW_W || r.x1 > camX + VIEW_W || r.y2 < camY - logicalH
                    || r.y1 > camY + logicalH) continue;
            List<GameData.P> list = GameData.props(r.id);
            for (GameData.P pr : list) drawProp(c, pr);
        }
    }

    private void drawProp(Canvas c, GameData.P pr) {
        int dark = 0xff0b1725;
        switch (pr.type) {
            case GameData.CONSOLE: {
                box(c, 0x66000000, pr.x - 4, pr.y + pr.h - 4, pr.x + pr.w + 4, pr.y + pr.h + 8, 8);
                box(c, dark, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 8);
                neonStroke(c, pr.color, 2, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 8);
                box(c, (pr.color & 0x00ffffff) | 0xaa000000, pr.x + 8, pr.y + 8, pr.x + pr.w - 8, pr.y + 28, 4);
                textC(c, "_", pr.x + pr.w / 2f, pr.y + 26, 12, 0xff06121c, true);
                break;
            }
            case GameData.PLANT: {
                box(c, 0xff243a2c, pr.x + 8, pr.y + pr.h - 22, pr.x + pr.w - 8, pr.y + pr.h, 6);
                p.setColor(pr.color);
                c.drawCircle(pr.x + pr.w / 2f, pr.y + pr.h - 26, pr.w * 0.42f, p);
                p.setColor(0xff9dffb0);
                c.drawCircle(pr.x + pr.w / 2f - 6, pr.y + pr.h - 34, pr.w * 0.22f, p);
                break;
            }
            case GameData.BED: {
                box(c, 0xff16283a, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 10);
                box(c, 0xff2a4a63, pr.x + 6, pr.y + 6, pr.x + pr.w - 6, pr.y + pr.h - 26, 8);
                box(c, (pr.color & 0x00ffffff) | 0xaa000000, pr.x + 8, pr.y + pr.h - 46, pr.x + pr.w - 8, pr.y + pr.h - 30, 5);
                break;
            }
            case GameData.REACTOR: {
                float cx = pr.x + pr.w / 2f, cy = pr.y + pr.h / 2f;
                p.setColor(0x33ff7a45);
                c.drawCircle(cx, cy, pr.w * 0.52f, p);
                p.setColor(0xff12222f);
                c.drawCircle(cx, cy, pr.w * 0.42f, p);
                stroke(c, pr.color, 5, cx - pr.w * 0.42f, cy - pr.w * 0.42f, cx + pr.w * 0.42f, cy + pr.w * 0.42f, pr.w * 0.42f);
                float pulse = (float) (Math.sin(SystemClock.uptimeMillis() / 420.0) * 0.08 + 0.62);
                p.setColor((pr.color & 0x00ffffff) | 0xcc000000);
                c.drawCircle(cx, cy, pr.w * 0.22f * pulse, p);
                p.setColor(0xffffe1c9);
                c.drawCircle(cx, cy, pr.w * 0.08f, p);
                break;
            }
            case GameData.TANK: {
                box(c, 0xff12222f, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 14);
                box(c, (pr.color & 0x00ffffff) | 0x55000000, pr.x + 8, pr.y + 10, pr.x + pr.w - 8, pr.y + pr.h - 10, 10);
                stroke(c, pr.color, 2.5f, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 14);
                p.setColor(0xff0b1725);
                c.drawRect(pr.x + pr.w / 2f - 4, pr.y - 10, pr.x + pr.w / 2f + 4, pr.y + 6, p);
                break;
            }
            case GameData.CRATE: {
                box(c, 0xff152535, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 6);
                neonStroke(c, pr.color, 2, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 6);
                p.setColor((pr.color & 0x00ffffff) | 0x66000000);
                c.drawRect(pr.x + 8, pr.y + pr.h / 2f - 4, pr.x + pr.w - 8, pr.y + pr.h / 2f + 4, p);
                break;
            }
            case GameData.SCREEN: {
                box(c, dark, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 9);
                neonStroke(c, pr.color, 2.5f, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 9);
                p.setColor((pr.color & 0x00ffffff) | 0x88000000);
                for (int i = 0; i < 4; i++) {
                    float ly = pr.y + 14 + i * (pr.h - 22) / 4f;
                    c.drawRect(pr.x + 12, ly, pr.x + pr.w - 12 - i * 14, ly + 4, p);
                }
                break;
            }
            case GameData.TABLE: {
                box(c, 0xff16283a, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 10);
                neonStroke(c, 0xff2f5d7a, 2, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 10);
                break;
            }
            case GameData.VALVE: {
                p.setColor(0xff12222f);
                c.drawCircle(pr.x + pr.w / 2f, pr.y + pr.h / 2f, pr.w / 2f, p);
                stroke(c, pr.color, 4, pr.x + 3, pr.y + 3, pr.x + pr.w - 3, pr.y + pr.h - 3, pr.w / 2f);
                p.setColor(pr.color);
                c.drawRect(pr.x + pr.w / 2f - 3, pr.y + 6, pr.x + pr.w / 2f + 3, pr.y + pr.h - 6, p);
                break;
            }
            case GameData.RACK: {
                box(c, 0xff0d1b29, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 6);
                neonStroke(c, pr.color, 2, pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, 6);
                for (int i = 0; i < 5; i++) {
                    p.setColor((pr.color & 0x00ffffff) | 0x99000000);
                    c.drawRect(pr.x + 10, pr.y + 14 + i * 28, pr.x + pr.w - 10, pr.y + 24 + i * 28, p);
                }
                break;
            }
            case GameData.DISH: {
                float cx = pr.x + pr.w / 2f, cy = pr.y + pr.h / 2f;
                p.setColor(0x22ffd166);
                c.drawCircle(cx, cy, pr.w * 0.55f, p);
                stroke(c, pr.color, 4, cx - pr.w * 0.34f, cy - pr.w * 0.34f,
                        cx + pr.w * 0.34f, cy + pr.w * 0.34f, pr.w * 0.34f);
                p.setColor(0xff12222f);
                c.drawCircle(cx, cy, 12, p);
                p.setColor(pr.color);
                c.drawCircle(cx, cy, 6, p);
                break;
            }
            default:
                break;
        }
    }

    private void drawNpcs(Canvas c) {
        for (GameData.N n : GameData.NPCS) {
            if (n.x < camX - VIEW_W || n.x > camX + VIEW_W || n.y < camY - logicalH
                    || n.y > camY + logicalH) continue;
            GameData.M m = GameData.mission(n.mission);
            boolean done = m != null && host.isDone(m.id);
            float bob = (float) Math.sin(SystemClock.uptimeMillis() / 620.0 + n.x) * 1.4f;
            person(c, n.x, n.y + bob, n.color, false, "down");
            textC(c, n.name, n.x, n.y + 22, 9, 0xffcfe9ff, true);
            p.setColor(done ? 0xff42f5a7 : 0xffffd166);
            c.drawCircle(n.x + 20, n.y - 42, 8, p);
            textC(c, done ? "v" : "!", n.x + 20, n.y - 38, 10, 0xff04121c, true);
        }
    }

    private void drawPlayer(Canvas c, boolean moving) {
        person(c, px, py, 0xffa278ff, moving, facing);
        p.setColor(0x332f9dff);
        c.drawCircle(px, py, 30, p);
    }

    private void person(Canvas c, float x, float y, int suit, boolean moving, String face) {
        float step = moving ? (float) Math.sin(walk) * 5f : 0f;
        p.setColor(0x66000000);
        c.drawOval(new RectF(x - 16, y - 4, x + 16, y + 8), p);

        box(c, 0xff16202f, x - 13, y - 14, x - 2, y + step, 5);
        box(c, 0xff16202f, x + 2, y - 14, x + 13, y - step, 5);

        int body = suit;
        int shade = (body & 0x00ffffff) | 0xcc000000;
        box(c, shade, x - 14, y - 42, x + 14, y - 8, 11);
        box(c, body, x - 13, y - 44, x + 13, y - 34, 8);

        int visorLight = 0xffbff4ff;
        if (face.equals("left")) {
            box(c, 0xff0d2030, x - 13, y - 38, x - 2, y - 22, 6);
            box(c, visorLight, x - 12, y - 36, x - 5, y - 26, 4);
        } else if (face.equals("right")) {
            box(c, 0xff0d2030, x + 2, y - 38, x + 13, y - 22, 6);
            box(c, visorLight, x + 5, y - 36, x + 12, y - 26, 4);
        } else {
            box(c, 0xff0d2030, x - 10, y - 38, x + 10, y - 24, 6);
            box(c, visorLight, x - 7, y - 36, x + 6, y - 27, 4);
        }
        p.setColor(0x33ffffff);
        c.drawCircle(x - 7, y - 41, 3, p);
    }

    private void drawBubble(Canvas c) {
        if (bubbleText == null) return;
        RectF r = bubbleRect();
        if (r == null) return;
        p.setColor(0x6600e5ff);
        c.drawRoundRect(new RectF(r.left - 3, r.top - 3, r.right + 3, r.bottom + 3), 14, 14, p);
        box(c, 0xf00a1424, r.left, r.top, r.right, r.bottom, 13);
        stroke(c, 0xff3fe3ff, 1.6f, r.left, r.top, r.right, r.bottom, 13);

        float sx = Math.max(r.left + 20, Math.min(r.right - 20, bubbleWorldX - camX + VIEW_W / 2f));
        Path tail = new Path();
        tail.moveTo(sx - 9, r.bottom - 1);
        tail.lineTo(sx, r.bottom + 13);
        tail.lineTo(sx + 9, r.bottom - 1);
        tail.close();
        p.setColor(0xff3fe3ff);
        c.drawPath(tail, p);
        p.setColor(0xf00a1424);
        Path inner = new Path();
        inner.moveTo(sx - 6, r.bottom - 4);
        inner.lineTo(sx, r.bottom + 9);
        inner.lineTo(sx + 6, r.bottom - 4);
        inner.close();
        c.drawPath(inner, p);

        String who = bubbleNpc != null ? bubbleNpc.name : "NOVA";
        text(c, who, r.left + 12, r.top + 19, 10, 0xff3fe3ff, true);
        StaticLayout layout = bubbleLayout(r.width() - 24);
        c.save();
        c.clipRect(r.left + 12, r.top + 25, r.right - 12, r.bottom - 32);
        c.translate(r.left + 12, r.top + 25 - bubbleScroll);
        layout.draw(c);
        c.restore();
        if (layout.getHeight() > r.height() - 62) {
            text(c, "swipe to read", r.right - 82, r.bottom - 12, 8.5f, 0xff6ea6bf, false);
        }
        if (bubbleAction != null) {
            box(c, 0xff15707f, r.left + 12, r.bottom - 24, r.left + 12 + 112, r.bottom - 7, 8);
            textC(c, bubbleAction, r.left + 68, r.bottom - 12, 9.5f, 0xffffffff, true);
        } else {
            text(c, "tap to close", r.left + 12, r.bottom - 12, 8.5f, 0xff6ea6bf, false);
        }
    }

    private StaticLayout bubbleLayout(float width) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffe3f6ff);
        paint.setTextSize(11.5f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return new StaticLayout(bubbleText, paint, Math.max(1, (int) width),
                Layout.Alignment.ALIGN_NORMAL, 1.15f, 0, false);
    }

    private void drawHud(Canvas c, boolean moving) {
        box(c, 0xd2071220, 0, 0, VIEW_W, 54, 0);
        p.setColor(0x5529e6ff);
        c.drawRect(0, 54, VIEW_W, 55, p);

        box(c, 0xcc122436, 8, 8, 56, 46, 12);
        neonStroke(c, 0xff4fd8ff, 1.5f, 8, 8, 56, 46, 12);
        for (int i = 0; i < 3; i++) {
            p.setColor(0xff9fe9ff);
            c.drawRect(18, 19 + i * 9, 46, 22 + i * 9, p);
        }

        text(c, "BLACKSTAR", 68, 20, 13, 0xffe3f6ff, true);
        text(c, "SYSTEMD DECK  /  " + doneCount() + " OF " + GameData.MISSIONS.size() + " SYSTEMS",
                68, 36, 8.5f, 0xff5fb9d4, false);

        box(c, 0xcc122436, 290, 8, 392, 46, 12);
        neonStroke(c, 0xffd166ff, 1.5f, 290, 8, 392, 46, 12);
        textC(c, "BADGES", 341, 30, 10, 0xfff1d6ff, true);

        GameData.M next = nextMission();
        if (next != null) {
            box(c, 0xaa0a1626, 10, 62, VIEW_W - 10, 88, 8);
            p.setColor(0xff2b6f8f);
            c.drawRect(10, 62, 14, 88, p);
            text(c, next.objective, 22, 79, 8.6f, 0xff9fe9ff, false);
        }

        if (roomCard != null && SystemClock.uptimeMillis() - roomCardAt < 2600) {
            float a = 1f - Math.max(0f, (SystemClock.uptimeMillis() - roomCardAt - 1900) / 700f);
            int alpha = (int) (Math.max(0f, a) * 255);
            p.setColor((alpha / 4 << 24) | 0x000a1424);
            c.drawRoundRect(new RectF(40, logicalH / 2f - 46, VIEW_W - 40, logicalH / 2f - 6), 10, 10, p);
            textC(c, roomCard, VIEW_W / 2f, logicalH / 2f - 20,
                    13, (alpha << 24) | 0x9fe9ff, true);
        }

        float joyCX = 86, joyCY = logicalH - 118;
        p.setColor(0x331d4a63);
        c.drawCircle(joyCX, joyCY, 74, p);
        p.setColor(0x66ffffff);
        c.drawCircle(joyCX, joyCY, 3, p);
        float kx = joyCX + Math.max(-52, Math.min(52, joyX * 0.9f));
        float ky = joyCY + Math.max(-52, Math.min(52, joyY * 0.9f));
        p.setColor(0xcc1a3a52);
        c.drawCircle(joyCX, joyCY, 58, p);
        stroke(c, 0xff4fd8ff, 1.6f, joyCX - 58, joyCY - 58, joyCX + 58, joyCY + 58, 58);
        p.setColor(0xdd4fd8ff);
        c.drawCircle(kx, ky, 22, p);
        p.setColor(0xff07131f);
        c.drawCircle(kx, ky, 9, p);
        text(c, "WALK", joyCX - 13, joyCY + 96, 9, 0xff6ea6bf, true);

        float actionCX = 330, actionCY = logicalH - 118;
        p.setColor(0x4415b6d8);
        c.drawCircle(actionCX, actionCY, 58, p);
        p.setColor(0xdd167b93);
        c.drawCircle(actionCX, actionCY, 47, p);
        stroke(c, 0xff7ff0ff, 2, actionCX - 47, actionCY - 47, actionCX + 47, actionCY + 47, 47);
        textC(c, "ACTION", actionCX, actionCY + 5, 11, 0xffffffff, true);
        if (!moving && bubbleText == null) {
            textC(c, "tap to talk / use", actionCX, actionCY + 74, 8, 0xff6ea6bf, false);
        }
    }

    private void trackRoom(long now) {
        String found = "";
        for (GameData.R r : GameData.ROOMS) {
            if (inRect(px, py, r.x1, r.y1, r.x2, r.y2)) {
                found = r.name;
                break;
            }
        }
        if (!found.equals(currentRoom)) {
            currentRoom = found;
            if (!found.isEmpty()) {
                roomCard = found;
                roomCardAt = now;
            }
        }
    }

    private int doneCount() {
        int n = 0;
        for (GameData.M m : GameData.MISSIONS) if (host.isDone(m.id)) n++;
        return n;
    }

    private GameData.M nextMission() {
        for (String id : GameData.ORDER) {
            GameData.M m = GameData.mission(id);
            if (m != null && !host.isDone(id)) return m;
        }
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        last = 0;
        resetJoy();
    }

    @SuppressWarnings("unused")
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((alpha & 0xff) << 24);
    }
}
