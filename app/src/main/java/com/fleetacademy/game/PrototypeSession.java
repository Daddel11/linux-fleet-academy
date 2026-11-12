package com.fleetacademy.game;

import android.content.SharedPreferences;
import android.util.Base64;

import com.fleetacademy.game.simulator.Simulator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class PrototypeSession {
    private final SharedPreferences preferences;
    private Simulator simulator;
    private final Map<String, Boolean> conditions = new HashMap<>();

    PrototypeSession(SharedPreferences preferences) throws IOException {
        this.preferences = preferences;
        String saved = preferences.getString("snapshot", null);
        try {
            simulator = saved == null ? new Simulator() : Simulator.load(Base64.decode(saved, Base64.NO_WRAP));
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new IOException("Invalid prototype snapshot", e);
        }
        refreshConditions();
    }

    Simulator.Result execute(String command) {
        Simulator.Result result = simulator.execute(command);
        if (!result.mutations.isEmpty()) refreshConditions();
        if (!result.mutations.isEmpty() && !preferences.edit()
                .putString("snapshot", Base64.encodeToString(simulator.save(), Base64.NO_WRAP)).commit()) {
            throw new IllegalStateException("Could not save prototype state; progress may be lost.");
        }
        return result;
    }

    boolean condition(String key) {
        return Boolean.TRUE.equals(conditions.get(key));
    }

    private void refreshConditions() {
        for (String key : new String[]{"storage", "index", "cooling", "engine", "galley"}) {
            conditions.put(key, readCondition(key));
        }
    }

    private boolean readCondition(String key) {
        switch (key) {
            case "storage":
                Simulator.Result listing = simulator.execute("ls -l /storage");
                for (String line : listing.stdout.split("\n")) {
                    if (line.startsWith("d") && line.endsWith(" robot")) return true;
                }
                return false;
            case "index": return active("storage-index.service");
            case "cooling": return active("engine-cooling.service");
            case "engine": return active("engine-core.service");
            case "galley": return active("galley-printer.service");
            default: return false;
        }
    }

    private boolean active(String unit) {
        Simulator.Result result = simulator.execute("systemctl status " + unit);
        return result.exitCode == 0 && result.stdout.equals(unit + " - active\n");
    }
}
