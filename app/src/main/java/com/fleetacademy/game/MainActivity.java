package com.fleetacademy.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements ShipView.Host, MenuView.Host {

    private static final int BG = 0xff0a1424;
    private static final int PANEL = 0xff0f2033;
    private static final int NEON = 0xff4fd8ff;
    private static final int TEXT = 0xffe3f6ff;
    private static final int DIM = 0xff6ea6bf;

    private SharedPreferences prefs;
    private final Set<String> done = new HashSet<>();
    private ShipView ship;
    private SimShell shell;
    private boolean inGame;
    private Dialog openDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xff04070f));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applySystemBarInsets(getWindow());
        prefs = getSharedPreferences("blackstar_chapter1_v1", MODE_PRIVATE);
        done.addAll(prefs.getStringSet("done", new HashSet<String>()));
        shell = new SimShell(null, done);
        shell.restore(prefs.getString("shell_state", null));
        shell.setListener((cmd, output) -> saveShell());
        showMenu();
    }

    /* ------------------------------------------------------------- screen */

    private void showMenu() {
        inGame = false;
        boolean started = prefs.getBoolean("started", false);
        setContentView(new MenuView(this, this, started, done.size()));
    }

    @Override
    public void onContinue() {
        startGame(false);
    }

    @Override
    public void onNewGame() {
        if (prefs.getBoolean("started", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Restart Blackstar?")
                    .setMessage("Blackstar progress will be reset. Your previous Fleet save will be preserved.")
                    .setPositiveButton("Restart", (d, w) -> {
                        ship = null;
                        done.clear();
                        shell = new SimShell(null, done);
                        shell.setListener((cmd, output) -> saveShell());
                        SharedPreferences.Editor reset = prefs.edit().remove("done").remove("shell_state")
                                .remove("x").remove("y").remove("pending_comic");
                        for (String key : prefs.getAll().keySet()) {
                            if (key.startsWith("comic_")) reset.remove(key);
                        }
                        reset.putBoolean("legacy_imported", true).putBoolean("started", true).apply();
                        openWorld(true);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        prefs.edit().putBoolean("started", true).apply();
        openWorld(true);
    }

    private void startGame(boolean fresh) {
        prefs.edit().putBoolean("started", true).apply();
        openWorld(fresh);
    }

    private void openWorld(boolean withIntro) {
        final Runnable enter = () -> {
            inGame = true;
            Map<String, ?> values = prefs.getAll();
            float x = PositionPreferences.coordinate(values.get("x"), 900f);
            float y = PositionPreferences.coordinate(values.get("y"), 4820f);
            ship = new ShipView(this, this, x, y);
            setContentView(ship);
            Window win = getWindow();
            win.setStatusBarColor(0xff04070f);
            win.setNavigationBarColor(0xff04070f);
        };
        String pending = prefs.getString("pending_comic", null);
        if (pending != null) {
            playComic(pending, enter);
        } else if (withIntro || !prefs.getBoolean("comic_intro", false)) {
            playComic("intro", enter);
        } else {
            enter.run();
        }
    }

    @Override
    public void onBackPressed() {
        if (inGame) {
            pauseMenu();
        } else {
            super.onBackPressed();
        }
    }

    /* ------------------------------------------------------------- ship io */

    @Override
    public boolean isDone(String mission) {
        return done.contains(mission);
    }

    @Override
    public void talk(GameData.N npc) {
        GameData.M m = GameData.mission(npc.mission);
        if (m == null) return;
        if (isDone(m.id)) {
            ship.say(npc, m.success, m.type == GameData.SHELL ? "OPEN TERMINAL" : null);
            return;
        }
        if (!available(m.id)) { ship.say(npc, "NOVA: This mission is still locked. Complete the prerequisites first.", null); return; }
        String action = m.type == GameData.QUIZ ? "START REVIEW"
                : m.type == GameData.SHELL ? "OPEN TERMINAL" : "OPEN CONSOLE";
        ship.say(npc, m.brief, action);
    }

    @Override
    public void openMission(GameData.M m) {
        if (m == null) return;
        if (isDone(m.id)) {
            if (m.type == GameData.SHELL) openTerminal(m);
            else ship.say(GameData.npc(m.npc), m.success, null);
            return;
        }
        if (!available(m.id)) { ship.say(GameData.npc(m.npc), "NOVA: This mission is still locked. Complete the prerequisites first.", null); return; }
        if (m.type == GameData.QUIZ) {
            startQuiz(m, 0, 0);
        } else if (m.type == GameData.SHELL) {
            openTerminal(m);
        } else {
            openConfig(m);
        }
    }

    @Override
    public void pauseMenu() {
        if (ship != null) {
            prefs.edit().putFloat("x", ship.x()).putFloat("y", ship.y()).apply();
        }
        new AlertDialog.Builder(this)
                .setTitle("PAUSED")
                .setItems(new String[]{"Resume", "Badge collection", "Galaxy map", "Main menu"},
                        (d, which) -> {
                            if (which == 1) badges();
                            else if (which == 2) showGalaxy(this::resumeGame);
                            else if (which == 3) showMenu();
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void resumeGame() {
        if (ship != null) {
            inGame = true;
            setContentView(ship);
        }
    }

    @Override
    public void badges() {
        showBadges(inGame && ship != null ? this::resumeGame : this::showMenu);
    }

    @Override
    public void onGalaxy() {
        showGalaxy(this::showMenu);
    }

    @Override
    public void onBadges() {
        showBadges(this::showMenu);
    }

    /* ------------------------------------------------------------ missions */

    private void solve(GameData.M m) {
        if (!available(m.id) || !shell.solved(m.id)) return;
        boolean first = done.add(m.id);
        if (!first) return;
        final String trigger = comicTrigger(m.id);
        SharedPreferences.Editor progress = prefs.edit().putStringSet("done", new HashSet<>(done));
        if (trigger != null && !prefs.getBoolean("comic_" + trigger, false))
            progress.putString("pending_comic", trigger);
        progress.apply();
        GameData.N npc = GameData.npc(m.npc);
        final Runnable after = () -> {
            if (ship != null) {
                if (m.type == GameData.SHELL) {
                    new AlertDialog.Builder(this).setTitle("MISSION COMPLETE: " + m.title)
                            .setMessage(m.success + "\n\nBadge earned: " + m.badge + ".")
                            .setPositiveButton("Close", null).show();
                } else {
                    ship.say(npc, m.success + "  Badge earned: " + m.badge + ".", null);
                }
                ship.invalidate();
            }
        };
        if (trigger != null) playComic(trigger, after);
        else after.run();
    }

    private String comicTrigger(String mission) {
        return null;
    }

    private boolean available(String id) {
        return GameData.available(id, done);
    }

    private void playComic(String trigger, Runnable after) {
        GameData.Comic comic = GameData.comic(trigger);
        if (comic == null || prefs.getBoolean("comic_" + trigger, false)) {
            if (after != null) after.run();
            return;
        }
        prefs.edit().putString("pending_comic", trigger).apply();
        final boolean[] fired = {false};
        final Runnable finish = () -> {
            if (fired[0]) return;
            fired[0] = true;
            if (after != null) after.run();
        };
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);
        ComicView view = new ComicView(this, comic, () -> {
            prefs.edit().putBoolean("comic_" + trigger, true).remove("pending_comic").apply();
            d.dismiss();
        }) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getY() > getHeight() * 0.94f && event.getX() > getWidth() * 0.7f)
                    return true;
                return super.onTouchEvent(event);
            }
        };
        d.setContentView(view);
        applySystemBarInsets(d.getWindow());
        d.setOnDismissListener(x -> finish.run());
        d.show();
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0xff04070f));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /* ------------------------------------------------------- config editor */

    private void openConfig(GameData.M m) {
        final Map<String, String> buf = new LinkedHashMap<>();
        for (GameData.F f : m.files) {
            String cur = shell.files().get(f.path);
            buf.put(f.path, cur == null ? f.initial : cur);
        }
        final int[] selected = {0};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(4));
        root.setBackgroundColor(PANEL);

        TextView head = label(m.objective, 11, NEON, true);
        root.addView(head);

        TextView timer = label("", 12, 0xffffd166, true);
        if (m.seconds > 0) {
            timer.setGravity(Gravity.END);
            root.addView(timer);
        }

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs);

        EditText editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setTextColor(0xffbff4ff);
        editor.setBackgroundColor(0xff06121c);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(7);
        editor.setMaxLines(12);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(8);
        root.addView(editor, ep);

        TextView error = label("", 10.5f, 0xffff8095, false);
        error.setPadding(0, dp(6), 0, 0);
        root.addView(error);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button terminalBtn = smallButton("TERMINAL");
        Button resetBtn = smallButton("RESET");
        Button applyBtn = smallButton("SAVE CONFIG");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        tools.addView(terminalBtn, half);
        tools.addView(resetBtn, half);
        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(0, -2, 1.6f);
        tools.addView(applyBtn, wide);
        root.addView(tools);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(m.title)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .create();
        openDialog = dialog;

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            GameData.F f = m.files.get(selected[0]);
            editor.setText(buf.get(f.path));
            editor.setSelection(editor.getText().length());
            for (int i = 0; i < tabs.getChildCount(); i++) {
                Button b = (Button) tabs.getChildAt(i);
                b.setTextColor(i == selected[0] ? 0xff04121c : 0xffbff4ff);
                b.setBackground(round(i == selected[0] ? NEON : 0xff122436, 8));
            }
        };

        for (int i = 0; i < m.files.size(); i++) {
            final int idx = i;
            Button b = smallButton(m.files.get(i).name());
            b.setTextSize(9.5f);
            b.setOnClickListener(v -> {
                buf.put(m.files.get(selected[0]).path, editor.getText().toString());
                selected[0] = idx;
                refresh[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = dp(4);
            tabs.addView(b, lp);
        }
        if (m.files.size() == 1) tabs.setVisibility(View.GONE);
        refresh[0].run();

        resetBtn.setOnClickListener(v -> {
            GameData.F f = m.files.get(selected[0]);
            buf.put(f.path, f.initial);
            editor.setText(f.initial);
            error.setText("");
        });
        terminalBtn.setOnClickListener(v -> openTerminal(null));
        applyBtn.setOnClickListener(v -> {
            buf.put(m.files.get(selected[0]).path, editor.getText().toString());
            if (!NetworkConfigValidator.validate(m.id, buf)) {
                error.setText("Simulator mission validation failed (not networkctl). "
                        + "Check the objective, sections, and values.");
                return;
            }
            for (GameData.F f : m.files) shell.files().put(f.path, buf.get(f.path));
            shell.reloaded = false;
            saveShell();
            dialog.dismiss();
            solve(m);
        });

        CountDownTimer cdt = null;
        if (m.seconds > 0) {
            cdt = new CountDownTimer(m.seconds * 1000L, 250) {
                @Override
                public void onTick(long left) {
                    long s = left / 1000;
                    timer.setText("OXYGEN BUFFER  " + (s / 60) + ":" + two(s % 60));
                }

                @Override
                public void onFinish() {
                    dialog.dismiss();
                    if (ship != null)
                        ship.say(GameData.npc(m.npc),
                                "The scrubber reset itself. Oxygen buffer empty. Take a breath and try again.",
                                "RETRY CONSOLE");
                }
            };
            cdt.start();
        }
        final CountDownTimer countdown = cdt;
        dialog.setOnDismissListener(d -> {
            openDialog = null;
            if (countdown != null) countdown.cancel();
            if (ship != null) ship.invalidate();
        });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            win.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.86f));
            win.setBackgroundDrawable(round(PANEL, 12));
        }
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : "" + v;
    }

    /* ------------------------------------------------------------ terminal */

    private void openTerminal(GameData.M mission) {
        GameData.M m = mission;
        if (m == null && ship != null) {
            m = null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(4), dp(10), dp(4));
        box.setBackgroundColor(PANEL);

        final TextView transcript = new TextView(this);
        transcript.setTextColor(0xffaef0c8);
        transcript.setBackgroundColor(0xff06121c);
        transcript.setTextSize(12);
        transcript.setTypeface(Typeface.MONOSPACE);
        transcript.setPadding(dp(10), dp(10), dp(10), dp(10));
        transcript.setText("BLACKSTAR TERMINAL - simulation only, no access to the device or server.\n"
                + (m != null ? "TASK: " + m.objective + "\n" : "")
                + "Tap GUIDE for the full briefing and steps; scroll to read.\n"
                + "Type help for commands. TAB completes commands and paths.\n");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(transcript);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        final EditText entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setTextSize(13);
        entry.setTypeface(Typeface.MONOSPACE);
        entry.setTextColor(0xffbff4ff);
        entry.setHint("enter a command");
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(entry);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button tab = smallButton("TAB");
        Button run = smallButton("RUN");
        Button hint = smallButton("GUIDE");
        Button editorButton = smallButton("EDITOR");
        row.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(run, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
        if (m != null && m.id.equals("medbay")) row.addView(editorButton, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ship console")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        openDialog = dialog;

        final GameData.M target = m;
        hint.setOnClickListener(v -> {
            if (target != null) new AlertDialog.Builder(this).setTitle(target.title + " / GUIDE")
                    .setMessage("BRIEFING\n\n" + target.brief + "\n\nOBJECTIVE\n\n"
                            + target.objective + "\n\nSTEPS AND HINTS\n\n" + target.hint
                            + (isDone(target.id) ? "\n\nMISSION COMPLETE\n\n" + target.success : ""))
                    .setPositiveButton("Close", null).show();
        });
        editorButton.setOnClickListener(v -> {
            EditText edit = new EditText(this);
            edit.setTypeface(Typeface.MONOSPACE);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            edit.setText(shell.files().get(SimShell.ENGINE));
            new AlertDialog.Builder(this).setTitle(SimShell.ENGINE).setView(edit)
                    .setPositiveButton("Save", (d, w) -> { shell.editUnit(edit.getText().toString()); saveShell(); })
                    .setNegativeButton("Cancel", null).show();
        });
        final Runnable execute = () -> {
            String line = entry.getText().toString();
            if (line.trim().isEmpty()) return;
            String prompt = shell.prompt();
            String result = shell.run(line);
            if (result.equals("__CLEAR__")) {
                transcript.setText("");
            } else {
                transcript.append("\n" + prompt + " $ " + line
                        + (result.isEmpty() ? "" : "\n" + result) + "\n");
            }
            if (transcript.length() > 14000) {
                CharSequence t = transcript.getText();
                transcript.setText(t.subSequence(t.length() - 9000, t.length()));
            }
            entry.setText("");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));

            if (target != null && !isDone(target.id) && target.type == GameData.SHELL && shell.solved(target.id)) {
                dialog.dismiss();
                solve(target);
            }
        };
        tab.setOnClickListener(v -> {
            SimShell.Completion c = shell.complete(entry.getText().toString(),
                    entry.getSelectionStart());
            entry.setText(c.text);
            entry.setSelection(Math.min(c.cursor, c.text.length()));
            if (c.note != null && !c.note.isEmpty()) {
                transcript.append("\n" + c.note + "\n");
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });
        run.setOnClickListener(v -> execute.run());
        entry.setOnEditorActionListener((v, action, event) -> {
            execute.run();
            return true;
        });
        dialog.setOnDismissListener(d -> {
            openDialog = null;
            if (ship != null) ship.invalidate();
        });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            win.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.8f));
            win.setBackgroundDrawable(round(PANEL, 12));
        }
    }

    private boolean shellMissionSolved(String id) {
        if (id.equals("server")) {
            return !shell.exists(SimShell.BAD_UPLINK)
                    && shell.exists(SimShell.NETDIR + "/10-uplink.network")
                    && shell.reloaded && shell.uplinkReconfigured;
        }
        return false;
    }

    private void saveShell() {
        prefs.edit().putString("shell_state", shell.snapshot()).apply();
    }

    /* ---------------------------------------------------------------- quiz */

    private void startQuiz(GameData.M m, int index, int score) {
        if (index >= GameData.QUIZ_Q.length) {
            if (score >= 4) {
                solve(m);
            } else if (ship != null) {
                ship.say(GameData.npc(m.npc),
                        "Not yet, Cadet. " + score + " of 5. Walk the deck, read the consoles, then come back.",
                        "TRY AGAIN");
            }
            return;
        }
        String[] row = GameData.QUIZ_Q[index];
        new AlertDialog.Builder(this)
                .setTitle("BRIDGE REVIEW  " + (index + 1) + "/" + GameData.QUIZ_Q.length)
                .setMessage(row[0])
                .setSingleChoiceItems(new String[]{row[1], row[2], row[3]}, -1, (d, which) -> {
                    d.dismiss();
                    int correct = GameData.QUIZ_A[index];
                    boolean ok = which == correct;
                    new AlertDialog.Builder(this)
                            .setTitle(ok ? "Correct" : "Not quite")
                            .setMessage(ok ? feedback(index, which)
                                    : "The answer was: " + row[correct + 1] + "\n\n" + feedback(index, correct))
                            .setPositiveButton("Next", (dd, ww) ->
                                    startQuiz(m, index + 1, score + (ok ? 1 : 0)))
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String feedback(int index, int which) {
        switch (index) {
            case 0:
                return "[Match] decides which interfaces a .network file applies to.";
            case 1:
                return "DHCP=yes asks for a dynamic lease instead of a fixed Address.";
            case 2:
                return "A .netdev with Kind=bond creates the bond device; a .network attaches it.";
            case 3:
                return "networkctl reload re-reads the .network and .netdev files.";
            default:
                return "A VLAN keeps the lab's traffic off the rest of the ship.";
        }
    }

    /* -------------------------------------------------------------- badges */

    private void showBadges(Runnable back) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(PANEL);

        int earned = 0;
        for (GameData.M m : GameData.MISSIONS) if (isDone(m.id)) earned++;
        root.addView(label(earned + " of " + GameData.MISSIONS.size() + " badges earned", 13, NEON, true));

        for (GameData.M m : GameData.MISSIONS) {
            boolean got = isDone(m.id);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(9), 0, dp(9));
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot = new TextView(this);
            dot.setText(got ? "v" : "-");
            dot.setTextSize(12);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(got ? 0xff04121c : 0xff5d7d92);
            dot.setBackground(round(got ? 0xff42f5a7 : 0xff16283a, 20));
            LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(30), dp(30));
            dp1.rightMargin = dp(12);
            row.addView(dot, dp1);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(label(m.badge, 13, got ? TEXT : 0xff7d97a9, true));
            col.addView(label(got ? m.badgeDesc : "Locked - " + m.objective, 10.5f,
                    got ? 0xff7ef0d0 : 0xff506b7d, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            root.addView(row);

            View line = new View(this);
            line.setBackgroundColor(0x1affffff);
            root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        List<String> legacyBadges = new ArrayList<>();
        for (String id : done) if (GameData.mission(id) == null) legacyBadges.add(id);
        java.util.Collections.sort(legacyBadges);
        if (!legacyBadges.isEmpty()) {
            root.addView(label("Legacy badges", 13, NEON, true));
            for (String id : legacyBadges) root.addView(label(id, 12, TEXT, false));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        final boolean[] fired = {false};
        final Runnable finish = () -> {
            if (fired[0]) return;
            fired[0] = true;
            openDialog = null;
            if (back != null) back.run();
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("BADGE COLLECTION")
                .setView(scroll)
                .setPositiveButton("Back", null)
                .create();
        openDialog = dialog;
        dialog.setOnDismissListener(d -> finish.run());
        dialog.show();
        Window win = openDialog.getWindow();
        if (win != null) {
            win.setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
            win.setBackgroundDrawable(round(PANEL, 12));
        }
    }

    /* -------------------------------------------------------------- galaxy */

    private void showGalaxy(Runnable back) {
        inGame = false;
        GalaxyView map = new GalaxyView(this, new GalaxyView.Host() {
            @Override
            public void onNode(int index) {
                String[] g = GameData.GALAXY[index];
                String body = index == 0
                        ? "Blackstar: Chapter 1 with six missions."
                        : "Not available in this version. Completing chapter one does not unlock this ship.";
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(g[0] + "  /  " + g[2])
                        .setMessage(g[1] + "\n\n" + body)
                        .setPositiveButton("Close", null)
                        .show();
            }

            @Override
            public void onBack() {
                if (back != null) back.run();
            }
        });
        setContentView(map);
        Window win = getWindow();
        win.setStatusBarColor(0xff04070f);
        win.setNavigationBarColor(0xff04070f);
    }

    /* --------------------------------------------------------------- utils */

    private void applySystemBarInsets(Window window) {
        if (Build.VERSION.SDK_INT < 35 || window == null) return;
        View content = window.findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        content.requestApplyInsets();
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(10.5f);
        b.setTextColor(0xffbff4ff);
        b.setBackground(round(0xff122436, 8));
        b.setPadding(dp(4), dp(2), dp(4), dp(2));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setFocusable(false);
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * getResources().getDisplayMetrics().density);
        return g;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ship != null) {
            prefs.edit().putFloat("x", ship.x()).putFloat("y", ship.y()).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getWindow() != null) {
            getWindow().setStatusBarColor(0xff04070f);
            getWindow().setNavigationBarColor(0xff04070f);
        }
    }

    @SuppressWarnings("unused")
    private int alpha(int color, int a) {
        return (color & 0x00ffffff) | (a << 24);
    }
}
