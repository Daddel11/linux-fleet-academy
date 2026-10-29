package com.fleetacademy.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;

class SimShell {
    interface Listener { void onCommand(String cmd, String output); }
    static final String NETDIR = "/etc/systemd/network", BAD_UPLINK = NETDIR + "/05-uplink-conflict.network";
    static final String ENGINE = "/etc/systemd/system/engine.service";
    static final String BACKUP = "/var/backups/bulkhead.conf", CONFIG = "/etc/blackstar/bulkhead.conf";
    static final String INITIAL_UNIT = "[Unit]\nDescription=Blackstar Engine Control\n\n[Service]\nType=simple\nExecStart=/opt/blackstar/bin/engine-control\n\n[Install]\nWantedBy=multi-user.target\n";
    final Map<String,String> files = new LinkedHashMap<>();
    final List<String> history = new ArrayList<>();
    Listener listener;
    final Set<String> done;
    boolean reloaded, uplinkReconfigured, diagnostics, inspected, power, oxygen, enabled, bulkhead, engine, flight;
    int fileRevision, loadedRevision, engineStartRevision = -1, exitCode;
    String loadedUnit = INITIAL_UNIT;

    SimShell(Listener listener) { this(listener, new HashSet<String>()); }
    SimShell(Listener listener, Set<String> done) { this.listener = listener; this.done = done; files.put(ENGINE, INITIAL_UNIT); files.put(BACKUP, "bulkhead=operational\n"); }
    void setListener(Listener l) { listener = l; }
    boolean exists(String path) { return files.containsKey(path); }
    Map<String,String> files() { return files; }
    String prompt() { return "linus@blackstar:~"; }
    void editUnit(String text) { if (!text.equals(files.get(ENGINE))) { files.put(ENGINE, text); fileRevision++; } }
    boolean dependencies(String text) {
        if (text == null) return false;
        boolean requires = false, after = false, unit = false, service = false;
        String type = null, exec = null;
        int execCount = 0;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("[")) { unit = line.equals("[Unit]"); service = line.equals("[Service]"); continue; }
            if (service && line.startsWith("Type=")) type = line.substring(5).trim();
            if (service && line.startsWith("ExecStart=")) { exec = line.substring(10).trim(); execCount = exec.isEmpty() ? 0 : execCount + 1; }
            if (!unit) continue;
            if (line.startsWith("Requires=")) { String v = line.substring(9).trim(); requires = v.isEmpty() ? false : requires || Arrays.asList(v.split("\\s+")).contains("power-grid.service"); }
            if (line.startsWith("After=")) { String v = line.substring(6).trim(); after = v.isEmpty() ? false : after || Arrays.asList(v.split("\\s+")).contains("power-grid.service"); }
        }
        return requires && after && "simple".equals(type) && execCount == 1 && "/opt/blackstar/bin/engine-control".equals(exec);
    }
    boolean solved(String id) {
        switch (id) {
            case "crew": return diagnostics && inspected;
            case "reactor": return power;
            case "life": return oxygen && enabled;
            case "canteen": return "bulkhead=operational\n".equals(files.get(CONFIG)) && bulkhead;
            case "medbay": return dependencies(files.get(ENGINE)) && fileRevision == loadedRevision && engineStartRevision == loadedRevision && engine;
            case "fuel": return flight && solved("reactor") && solved("life") && solved("canteen") && solved("medbay");
            default: return false;
        }
    }
    String snapshot() {
        try {
            JSONObject s = new JSONObject();
            s.put("version", 1); s.put("files", new JSONObject(files)); s.put("diagnostics", diagnostics); s.put("inspected", inspected);
            s.put("power", power); s.put("oxygen", oxygen); s.put("enabled", enabled); s.put("bulkhead", bulkhead);
            s.put("engine", engine); s.put("flight", flight); s.put("fileRevision", fileRevision);
            s.put("loadedRevision", loadedRevision); s.put("engineStartRevision", engineStartRevision); s.put("loadedUnit", loadedUnit);
            return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    void restore(String data) {
        if (data == null) return;
        try {
            JSONObject s = new JSONObject(data); if (s.getInt("version") != 1) return;
            JSONObject f = s.getJSONObject("files"); Map<String,String> loaded = new LinkedHashMap<>();
            java.util.Iterator<String> keys = f.keys(); while (keys.hasNext()) { String k = keys.next(); loaded.put(k, f.getString(k)); }
            if (!loaded.containsKey(ENGINE) || !loaded.containsKey(BACKUP)) return;
            files.clear(); files.putAll(loaded);
            diagnostics=s.getBoolean("diagnostics"); inspected=s.getBoolean("inspected"); power=s.getBoolean("power"); oxygen=s.getBoolean("oxygen"); enabled=s.getBoolean("enabled"); bulkhead=s.getBoolean("bulkhead"); engine=s.getBoolean("engine"); flight=s.getBoolean("flight");
            fileRevision=s.getInt("fileRevision"); loadedRevision=s.getInt("loadedRevision"); engineStartRevision=s.optInt("engineStartRevision", -1); loadedUnit=s.getString("loadedUnit");
        } catch (Exception e) { throw new IllegalArgumentException("Blackstar save unreadable; the original is preserved", e); }
    }
    String fail(String message) { exitCode=1; return message; }
    String run(String input) {
        String line=input.trim(); if (line.isEmpty()) return "";
        history.add(line); exitCode=0;
        String result = execute(line);
        if (listener != null) listener.onCommand(line.split("\\s+")[0], result);
        return result;
    }
    private String execute(String line) {
        if (line.matches(".*[|;><&`].*")) return fail("Shell operators and redirections are not supported.");
        String[] a=line.split("\\s+"); int i=0;
        if (a[0].equals("sudo")) { i++; if (a.length == 1) return fail("sudo: missing command"); }
        String cmd=a[i++]; List<String> args=Arrays.asList(a).subList(i,a.length);
        if (cmd.equals("clear")) return "__CLEAR__";
        if (cmd.equals("history")) { StringBuilder b = new StringBuilder(); for (String h : history) { if (b.length() > 0) b.append('\n'); b.append(h); } return b.toString(); }
        if (cmd.equals("help") || cmd.equals("man") || args.contains("--help")) return "Simulation, no access to the device. Commands: systemctl, journalctl, cp, cat, help, man, history, clear. Edit the unit file using EDITOR. No pipes or redirections.";
        if (cmd.equals("cat")) { if(args.size()!=1) return fail("cat: specify a file"); String v=files.get(args.get(0)); return v==null?fail("cat: file not found"):v; }
        if (cmd.equals("cp")) { if(args.size()!=2) return fail("cp: specify a source and destination"); if(!args.get(0).equals(BACKUP)||!args.get(1).equals(CONFIG)) return fail("cp: only the virtual backup can be restored"); if (!GameData.available("canteen", done) && !done.contains("canteen")) return fail("Mission still locked."); files.put(CONFIG,files.get(BACKUP)); return ""; }
        if (cmd.equals("journalctl")) {
            if (!args.contains("-u") && !args.contains("-b")) return fail("journalctl: use -u bulkhead.service or -b");
            if (args.contains("-u") && !args.contains("bulkhead.service")) return fail("journalctl: unknown unit");
            return "03:17 flight-control: Stopping Flight Control...\n03:17 flight-control: Deactivated successfully.\n03:18 bulkhead: Failed to load /etc/blackstar/bulkhead.conf: No such file\n03:18 nova: unknown identifier GHOST-17";
        }
        if (!cmd.equals("systemctl")) return fail(cmd+": command not available in this simulation");
        if (args.isEmpty()) return fail("systemctl: missing action");
        String verb=args.get(0);
        if (verb.equals("list-units") || verb.equals("--failed")) {
            for (String arg: args.subList(1,args.size())) if (!arg.equals("--type=service") && !arg.equals("--all")) return fail("systemctl: unknown option " + arg);
            diagnostics=true;
            return verb.equals("--failed") ? (bulkhead ? "0 loaded units listed." : "bulkhead.service loaded failed failed") : "power-grid.service " + state(power,false) + "\noxygen.service " + state(oxygen,false) + "\nbulkhead.service " + state(bulkhead,true) + "\nengine.service " + state(engine,false);
        }
        if (verb.equals("daemon-reload")) { if(args.size()!=1) return fail("systemctl: unexpected argument"); loadedUnit=files.get(ENGINE); loadedRevision=fileRevision; return "Unit configuration reloaded."; }
        boolean now=false; String unit=null;
        for (String arg:args.subList(1,args.size())) { if(arg.equals("--now")) now=true; else if(unit==null && !arg.startsWith("-")) unit=arg; else return fail("systemctl: invalid argument " + arg); }
        if (unit==null) return fail("systemctl: missing unit");
        if (!unit.endsWith(".service") && !unit.endsWith(".target")) unit += ".service";
        if (!Arrays.asList("power-grid.service","oxygen.service","bulkhead.service","engine.service","flight.target").contains(unit)) return fail("Unit " + unit + " not found.");
        if (now && !verb.equals("enable")) return fail("systemctl: --now is not supported here");
        if (verb.equals("start") || verb.equals("restart") || verb.equals("enable") || verb.equals("isolate")) {
            String mission = unit.equals("power-grid.service") ? "reactor" : unit.equals("oxygen.service") ? "life" : unit.equals("bulkhead.service") ? "canteen" : unit.equals("engine.service") ? "medbay" : "fuel";
            if (!done.contains(mission) && !GameData.available(mission, done)) return fail("Mission still locked.");
        }
        if (verb.equals("cat")) { if(unit.equals("engine.service")) return files.get(ENGINE); if(unit.equals("flight.target")) return "[Unit]\nRequires=power-grid.service oxygen.service bulkhead.service engine.service\nAfter=power-grid.service oxygen.service bulkhead.service engine.service\nAllowIsolate=yes"; return fail("No unit file available"); }
        if (verb.equals("list-dependencies")) { if(unit.equals("engine.service")) return "engine.service\n"+loadedUnit; if(unit.equals("flight.target")) return "flight.target\n  power-grid.service\n  oxygen.service\n  bulkhead.service\n  engine.service"; return unit; }
        boolean active=unit.equals("power-grid.service")?power:unit.equals("oxygen.service")?oxygen:unit.equals("bulkhead.service")?bulkhead:unit.equals("engine.service")?engine:flight;
        if (verb.equals("status")) { if(unit.equals("power-grid.service")) inspected=true; if (!active) exitCode=3; return "● " + unit + "\n   Loaded: loaded\n   Active: " + state(active,unit.equals("bulkhead.service")) + (unit.equals("bulkhead.service") && !bulkhead ? "\n   Failed to load /etc/blackstar/bulkhead.conf" : ""); }
        if (verb.equals("is-active")) { if(!active) exitCode=3; return active?"active":"inactive"; }
        if (verb.equals("is-enabled")) { boolean on=unit.equals("oxygen.service") && enabled; if(!on) exitCode=1; return on?"enabled":"disabled"; }
        if (verb.equals("enable") && unit.equals("oxygen.service")) { enabled=true; if(now) oxygen=true; return "oxygen.service enabled"; }
        if (verb.equals("isolate") && unit.equals("flight.target")) { if(!(solved("reactor") && solved("life") && solved("canteen") && solved("medbay"))) return fail("Flight mode denied: vital services or the engine configuration are not ready."); flight=true; return "flight.target active. Blackstar ready for flight."; }
        if (verb.equals("start") || verb.equals("restart")) {
            if(unit.equals("power-grid.service")) power=true;
            else if(unit.equals("oxygen.service")) oxygen=true;
            else if(unit.equals("bulkhead.service")) { if(!"bulkhead=operational\n".equals(files.get(CONFIG))) { bulkhead=false; return fail("bulkhead.service failed: configuration is missing or invalid."); } bulkhead=true; }
            else if(unit.equals("engine.service")) { if (!power || !dependencies(loadedUnit)) { engine=false; return fail("engine.service failed: power or a loaded Requires=/After= dependency is missing."); } engine=true; engineStartRevision=loadedRevision; }
            else return fail("Activate the target with isolate.");
            return unit+" started.";
        }
        return fail("systemctl: action not supported: " + verb);
    }
    private String state(boolean active, boolean failed) { return active?"active (running)":failed?"failed":"inactive (dead)"; }
    static final class Completion { final String text, note; final int cursor; Completion(String t,int c,String n){text=t;cursor=c;note=n;} }
    Completion complete(String text,int cursor) {
        cursor = Math.max(0, Math.min(cursor, text.length()));
        int start = cursor;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        String prefix = text.substring(start, cursor);
        String before = text.substring(0, start).trim();
        boolean command = before.isEmpty() || before.equals("sudo");
        List<String> candidates = new ArrayList<>();
        if (command) {
            candidates.addAll(Arrays.asList("sudo", "systemctl", "journalctl", "cp", "cat", "help", "man", "history", "clear"));
        } else if (prefix.startsWith("/")) {
            candidates.addAll(files.keySet());
            if (!candidates.contains(CONFIG)) candidates.add(CONFIG);
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) if (candidate.startsWith(prefix)) matches.add(candidate);
        if (matches.isEmpty()) return new Completion(text, cursor, "");
        String common = matches.get(0);
        for (String match : matches) {
            int n = 0;
            while (n < common.length() && n < match.length() && common.charAt(n) == match.charAt(n)) n++;
            common = common.substring(0, n);
        }
        String suffix = text.substring(cursor);
        String inserted = common;
        if (matches.size() == 1 && suffix.isEmpty()) inserted += " ";
        StringBuilder note = new StringBuilder();
        if (matches.size() > 1) for (String match : matches) {
            if (note.length() > 0) note.append("  ");
            note.append(match);
        }
        return new Completion(text.substring(0, start) + inserted + suffix,
                start + inserted.length(), note.toString());
    }
}
