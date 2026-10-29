package com.fleetacademy.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GameData {

    static final int WORLD_W = 1800;
    static final int WORLD_H = 5100;

    static final int CONSOLE = 0, PLANT = 1, BED = 2, REACTOR = 3, TANK = 4,
            CRATE = 5, SCREEN = 6, TABLE = 7, VALVE = 8, RACK = 9, DISH = 10;

    static final int CONFIG = 0, SHELL = 1, QUIZ = 2;

    static final class F {
        final String path, initial;
        final String[] req, forbid;

        F(String path, String initial, String[] req, String[] forbid) {
            this.path = path;
            this.initial = initial;
            this.req = req;
            this.forbid = forbid;
        }

        String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    static final class M {
        final String id, room, npc, title, brief, objective, hint, success, badge, badgeDesc;
        final List<F> files = new ArrayList<>();
        int type = CONFIG;
        int seconds;

        M(String id, String room, String npc, String title, String brief, String objective,
          String hint, String success, String badge, String badgeDesc) {
            this.id = id;
            this.room = room;
            this.npc = npc;
            this.title = title;
            this.brief = brief;
            this.objective = objective;
            this.hint = hint;
            this.success = success;
            this.badge = badge;
            this.badgeDesc = badgeDesc;
        }

        M file(F f) {
            files.add(f);
            return this;
        }

        M type(int t) {
            type = t;
            return this;
        }

        M limit(int s) {
            seconds = s;
            return this;
        }
    }

    static final class R {
        final String id, name;
        final int x1, y1, x2, y2, accent;
        final String npc;

        R(String id, String name, int x1, int y1, int x2, int y2, int accent, String npc) {
            this.id = id;
            this.name = name;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.accent = accent;
            this.npc = npc;
        }

        int cx() { return (x1 + x2) / 2; }
        int cy() { return (y1 + y2) / 2; }
    }

    static final class D {
        final int x1, y1, x2, y2;
        final String[] needs;
        final String label;
        final boolean gate;

        D(int x1, int y1, int x2, int y2, String label, boolean gate, String... needs) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.label = label;
            this.gate = gate;
            this.needs = needs;
        }
    }

    static final class N {
        final String id, name;
        final int x, y, color;
        final String mission;

        N(String id, String name, int x, int y, int color, String mission) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.color = color;
            this.mission = mission;
        }
    }

    static final class P {
        final int type, x, y, w, h, color;

        P(int type, int x, int y, int w, int h, int color) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.color = color;
        }
    }

    static final class Panel {
        final int art, color;
        final String who, text;

        Panel(int art, int color, String who, String text) {
            this.art = art;
            this.color = color;
            this.who = who;
            this.text = text;
        }
    }

    static final class Comic {
        final String trigger;
        final List<Panel> panels = new ArrayList<>();

        Comic(String trigger) {
            this.trigger = trigger;
        }

        Comic p(int art, int color, String who, String text) {
            panels.add(new Panel(art, color, who, text));
            return this;
        }
    }

    /* ---------------------------------------------------------------- rooms */

    static final List<R> ROOMS = new ArrayList<>();
    static final List<D> DOORS = new ArrayList<>();
    static final List<N> NPCS = new ArrayList<>();
    static final List<M> MISSIONS = new ArrayList<>();
    static final List<Comic> COMICS = new ArrayList<>();
    static final Map<String, List<P>> PROPS = new LinkedHashMap<>();

    static final String[] ORDER = {
            "crew", "canteen", "medbay", "reactor", "life",
            "fuel", "science", "armory", "server", "bridge", "comm"
    };

    static final int SPINE_X1 = 830, SPINE_X2 = 970, SPINE_Y1 = 1500, SPINE_Y2 = 4400;
    static final int LINK_Y1 = 800, LINK_Y2 = 900;

    static R room(String id) {
        for (R r : ROOMS) if (r.id.equals(id)) return r;
        return ROOMS.get(0);
    }

    static M mission(String id) {
        for (M m : MISSIONS) if (m.id.equals(id)) return m;
        return null;
    }

    static boolean available(String id, Set<String> done) {
        if ("crew".equals(id)) return true;
        if (!done.contains("crew")) return false;
        if ("medbay".equals(id)) return done.contains("reactor") && done.contains("canteen");
        if ("fuel".equals(id)) return done.contains("reactor") && done.contains("life") && done.contains("canteen") && done.contains("medbay");
        return "reactor".equals(id) || "life".equals(id) || "canteen".equals(id);
    }

    static N npc(String id) {
        for (N n : NPCS) if (n.id.equals(id)) return n;
        return null;
    }

    static Comic comic(String trigger) {
        for (Comic c : COMICS) if (c.trigger.equals(trigger)) return c;
        return null;
    }

    static List<P> props(String room) {
        List<P> l = PROPS.get(room);
        return l == null ? new ArrayList<P>() : l;
    }

    private static void prop(String room, int type, int x, int y, int w, int h, int color) {
        List<P> l = PROPS.get(room);
        if (l == null) {
            l = new ArrayList<>();
            PROPS.put(room, l);
        }
        l.add(new P(type, x, y, w, h, color));
    }

    private static F f(String path, String initial, String[] req, String[] forbid) {
        return new F(path, initial, req, forbid);
    }

    private static String[] a(String... v) {
        return v;
    }

    static {
        ROOMS.add(new R("crew", "CREW QUARTERS", 180, 4400, 1620, 4960, 0xff37e0c8, "nova"));
        ROOMS.add(new R("canteen", "CANTEEN / HYDROPONICS", 180, 3700, 830, 4260, 0xff7ef06a, "pip"));
        ROOMS.add(new R("medbay", "MEDBAY", 970, 3700, 1620, 4260, 0xff5fd0ff, "rusk"));
        ROOMS.add(new R("reactor", "REACTOR CORE", 180, 3000, 830, 3560, 0xffff7a45, "iona"));
        ROOMS.add(new R("life", "LIFE SUPPORT", 970, 3000, 1620, 3560, 0xff59f7ff, "koa"));
        ROOMS.add(new R("fuel", "FUEL STORAGE", 180, 2300, 830, 2860, 0xffffc857, "bolt"));
        ROOMS.add(new R("science", "SCIENCE LABS", 970, 2300, 1620, 2860, 0xffb98cff, "mira"));
        ROOMS.add(new R("armory", "ARMORY / SHIELD CONTROL", 180, 1600, 830, 2160, 0xffff5d8f, "mara"));
        ROOMS.add(new R("server", "CORE MATRIX", 970, 1600, 1620, 2160, 0xff42f5a7, "nova2"));
        ROOMS.add(new R("bridge", "COMMAND BRIDGE", 400, 900, 1400, 1500, 0xff4fd8ff, "vega"));
        ROOMS.add(new R("comm", "COMM ARRAY", 400, 300, 1400, 800, 0xffffd166, "sol"));

        DOORS.add(new D(870, 4370, 930, 4430, "QUARTERS", true, "crew"));
        DOORS.add(new D(800, 3940, 860, 4060, "CANTEEN", false));
        DOORS.add(new D(940, 3940, 1000, 4060, "MEDBAY", false));
        DOORS.add(new D(800, 3240, 860, 3360, "REACTOR", false));
        DOORS.add(new D(940, 3240, 1000, 3360, "LIFE SUPPORT", false));
        DOORS.add(new D(800, 2540, 860, 2660, "FUEL", false));
        DOORS.add(new D(940, 2540, 1000, 2660, "SCIENCE", false));
        DOORS.add(new D(800, 1840, 860, 1960, "ARMORY", false));
        DOORS.add(new D(940, 1840, 1000, 1960, "CORE MATRIX", false));
        DOORS.add(new D(870, 1465, 930, 1535, "BRIDGE", false));
        DOORS.add(new D(830, 1540, 970, 1600, "BULKHEAD 4", true, "armory", "server"));
        DOORS.add(new D(830, 2230, 970, 2290, "BULKHEAD 3", true, "fuel", "science"));
        DOORS.add(new D(830, 2930, 970, 2990, "BULKHEAD 2", true, "reactor", "life"));
        DOORS.add(new D(830, 3630, 970, 3690, "BULKHEAD 1", true, "canteen", "medbay"));
        DOORS.add(new D(830, 828, 970, 892, "COMM ARRAY", true, "bridge"));

        NPCS.add(new N("nova", "NOVA", 1180, 4590, 0xff4fd8ff, "crew"));
        NPCS.add(new N("pip", "PIP", 470, 3830, 0xff7ef06a, "canteen"));
        NPCS.add(new N("rusk", "DR. RUSK", 1300, 3830, 0xff5fd0ff, "medbay"));
        NPCS.add(new N("iona", "IONA", 430, 3130, 0xffff7a45, "reactor"));
        NPCS.add(new N("koa", "KOA", 1330, 3130, 0xff59f7ff, "life"));
        NPCS.add(new N("bolt", "BOLT", 430, 2430, 0xffffc857, "fuel"));
        NPCS.add(new N("mira", "MIRA", 1330, 2430, 0xffb98cff, "science"));
        NPCS.add(new N("mara", "MARA", 430, 1730, 0xffff5d8f, "armory"));
        NPCS.add(new N("nova2", "NOVA CORE", 1330, 1730, 0xff42f5a7, "server"));
        NPCS.add(new N("vega", "CAPT. VEGA", 900, 1120, 0xffffd166, "bridge"));
        NPCS.add(new N("sol", "SOL", 900, 520, 0xffc7a7ff, "comm"));

        /* ------------------------------------------------------------ props */
        prop("crew", SCREEN, 590, 4440, 620, 70, 0xff37e0c8);
        prop("crew", CRATE, 870, 4590, 60, 65, 0xff2d4b63);
        prop("crew", CONSOLE, 980, 4590, 80, 55, 0xff37e0c8);
        prop("crew", CONSOLE, 340, 4540, 80, 55, 0xff37e0c8);
        prop("crew", CONSOLE, 1400, 4540, 80, 55, 0xff37e0c8);
        prop("crew", SCREEN, 260, 4810, 160, 60, 0xff4fd8ff);
        prop("crew", SCREEN, 1380, 4810, 160, 60, 0xff4fd8ff);

        prop("canteen", SCREEN, 250, 3750, 300, 70, 0xffff5d8f);
        prop("canteen", DISH, 225, 3840, 55, 55, 0xffff5d8f);
        prop("canteen", DISH, 700, 3760, 55, 55, 0xffff5d8f);
        prop("canteen", RACK, 230, 4040, 80, 130, 0xffff5d8f);
        prop("canteen", RACK, 330, 4040, 80, 130, 0xffff5d8f);
        prop("canteen", CONSOLE, 560, 3830, 80, 55, 0xffff5d8f);
        prop("canteen", CONSOLE, 540, 4070, 80, 55, 0xffff5d8f);

        prop("medbay", REACTOR, 1100, 3940, 200, 200, 0xffff7a45);
        prop("medbay", TANK, 1480, 4040, 90, 120, 0xff5a3a2a);
        prop("medbay", TANK, 1040, 3770, 80, 120, 0xff5a3a2a);
        prop("medbay", SCREEN, 1380, 3760, 160, 70, 0xffff7a45);
        prop("medbay", CONSOLE, 1200, 3830, 80, 55, 0xffff7a45);
        prop("medbay", VALVE, 1460, 3900, 60, 60, 0xffffc857);

        prop("reactor", RACK, 230, 3080, 80, 130, 0xffffc857);
        prop("reactor", RACK, 330, 3080, 80, 130, 0xffffc857);
        prop("reactor", RACK, 650, 3380, 80, 130, 0xffffc857);
        prop("reactor", RACK, 550, 3380, 80, 130, 0xffffc857);
        prop("reactor", SCREEN, 510, 3060, 190, 60, 0xffffc857);
        prop("reactor", CONSOLE, 230, 3280, 80, 55, 0xffff7a45);
        prop("reactor", CRATE, 700, 3060, 70, 70, 0xff2d4b63);

        prop("life", TANK, 1040, 3090, 100, 160, 0xff59f7ff);
        prop("life", TANK, 1170, 3090, 100, 160, 0xff59f7ff);
        prop("life", BED, 1040, 3320, 100, 150, 0xff59f7ff);
        prop("life", BED, 1420, 3310, 100, 150, 0xff59f7ff);
        prop("life", CONSOLE, 1200, 3410, 80, 55, 0xff59f7ff);
        prop("life", SCREEN, 1380, 3060, 200, 70, 0xff59f7ff);

        prop("fuel", SCREEN, 230, 2370, 250, 70, 0xff4fd8ff);
        prop("fuel", SCREEN, 560, 2370, 180, 70, 0xff4fd8ff);
        prop("fuel", DISH, 250, 2580, 100, 100, 0xff4fd8ff);
        prop("fuel", RACK, 380, 2640, 80, 130, 0xff4fd8ff);
        prop("fuel", CONSOLE, 560, 2620, 80, 55, 0xffffc857);

        prop("science", TABLE, 1040, 2400, 200, 90, 0xff2d4b63);
        prop("science", TABLE, 1280, 2400, 200, 90, 0xff2d4b63);
        prop("science", TANK, 1520, 2540, 80, 130, 0xffb98cff);
        prop("science", SCREEN, 1040, 2320, 260, 60, 0xffb98cff);
        prop("science", CONSOLE, 1180, 2600, 80, 55, 0xffb98cff);
        prop("science", PLANT, 1500, 2700, 70, 70, 0xff8f6bff);

        prop("armory", CRATE, 220, 1680, 100, 90, 0xffff5d8f);
        prop("armory", CRATE, 340, 1680, 100, 90, 0xffff5d8f);
        prop("armory", CRATE, 220, 1800, 100, 90, 0xffff5d8f);
        prop("armory", SCREEN, 460, 1660, 220, 70, 0xffff5d8f);
        prop("armory", CONSOLE, 480, 1900, 80, 55, 0xffff5d8f);
        prop("armory", CONSOLE, 480, 1980, 80, 55, 0xffff5d8f);

        prop("server", RACK, 1040, 1680, 90, 170, 0xff42f5a7);
        prop("server", RACK, 1150, 1680, 90, 170, 0xff42f5a7);
        prop("server", RACK, 1260, 1680, 90, 170, 0xff42f5a7);
        prop("server", RACK, 1040, 1890, 90, 170, 0xff42f5a7);
        prop("server", RACK, 1150, 1890, 90, 170, 0xff42f5a7);
        prop("server", CONSOLE, 1400, 2010, 80, 55, 0xff42f5a7);

        prop("bridge", SCREEN, 520, 960, 760, 190, 0xff4fd8ff);
        prop("bridge", CRATE, 760, 1250, 70, 70, 0xff2d4b63);
        prop("bridge", CONSOLE, 1000, 1240, 80, 55, 0xff4fd8ff);
        prop("bridge", CONSOLE, 560, 1400, 80, 55, 0xff4fd8ff);
        prop("bridge", CONSOLE, 1240, 1400, 80, 55, 0xff4fd8ff);
        prop("bridge", CRATE, 420, 1240, 70, 70, 0xff2d4b63);

        prop("comm", DISH, 900, 420, 260, 260, 0xffffd166);
        prop("comm", CONSOLE, 620, 560, 80, 55, 0xffffd166);
        prop("comm", CONSOLE, 1120, 560, 80, 55, 0xffffd166);
        prop("comm", RACK, 420, 340, 90, 170, 0xffffd166);
        prop("comm", RACK, 1290, 340, 90, 170, 0xffffd166);

        /* --------------------------------------------------------- missions */
        MISSIONS.add(new M("crew", "crew", "nova", "GIVE ETH0 A STATIC ADDRESS",
                "Morning, Cadet. At 04:12 our DHCP server died. Every door on this deck is default-deny, "
                        + "and eth0 is still asking for a lease nobody can give. Give it a static address.",
                "GIVE ETH0 A STATIC ADDRESS - CREW QUARTERS CONSOLE",
                "Set Address=10.42.7.10/24, Gateway and DNS to 10.42.7.1, and switch DHCP off.",
                "eth0 is up at 10.42.7.10/24. Door controller online.",
                "Static Anchor", "Brought eth0 up without a DHCP server.")
                .file(f("/etc/systemd/network/10-eth0.network",
                        "[Match]\nName=eth0\n\n[Network]\nDHCP=yes\n",
                        a("Name=eth0", "Address=10.42.7.10/24", "Gateway=10.42.7.1", "DNS=10.42.7.1", "DHCP=no"),
                        a("DHCP=yes"))));

        MISSIONS.add(new M("canteen", "canteen", "pip", "PIN THE IRRIGATION SENSOR",
                "The irrigation controller changed its own address overnight, so now the pump waters "
                        + "the ceiling. Pin sensor0 to 10.42.7.40 and never let it wander again.",
                "PIN SENSOR0 TO 10.42.7.40 - CANTEEN CONSOLE",
                "Replace the dynamic config with one exact Address in 10.42.7.0/24 and disable DHCP.",
                "sensor0 is pinned at 10.42.7.40/24. The basil is safe.",
                "Garden Keeper", "Stopped a sensor from moving house.")
                .file(f("/etc/systemd/network/20-sensor0.network",
                        "[Match]\nName=sensor0\n\n[Network]\nDHCP=yes\nAddress=10.42.7.99/24\n",
                        a("Name=sensor0", "Address=10.42.7.40/24", "DHCP=no"),
                        a("Address=10.42.7.99", "DHCP=yes"))));

        MISSIONS.add(new M("medbay", "medbay", "rusk", "ROUTE THE HEART MONITOR",
                "My cryo patient's monitor sends to the bridge subnet 10.42.8.0/24. Right now those "
                        + "packets just drift off into space. Give them a route.",
                "ADD A ROUTE TO 10.42.8.0/24 - MEDBAY CONSOLE",
                "Add a [Route] section with Destination=10.42.8.0/24 and the ship gateway 10.42.7.1.",
                "Route added. The monitor is talking to the bridge again.",
                "Pulse Keeper", "Routed a patient's heartbeat home.")
                .file(f("/etc/systemd/network/10-monitor.network",
                        "[Match]\nName=med0\n\n[Network]\nAddress=10.42.7.30/24\nGateway=10.42.7.1\n",
                        a("Destination=10.42.8.0/24", "[Route]", "Gateway=10.42.7.1"),
                        a())));

        MISSIONS.add(new M("reactor", "reactor", "iona", "BOND THE POWER CABLES",
                "Two power cables, one bond. Telemetry is overloading a single line. Build bond0 from "
                        + "eth1 and eth2 - and do it properly, with a netdev file.",
                "CREATE BOND0 - REACTOR CONSOLE",
                "Create bond0 in a .netdev; configure its address, then attach eth1 and eth2 in their own .network files.",
                "bond0 is up. Two cables, one steady heartbeat of power.",
                "Core Binder", "Bonded two cables into one link.")
                .file(f("/etc/systemd/network/10-bond0.netdev",
                        "[NetDev]\nName=bond0\nKind=dummy\n\n[Bond]\nMode=balance-rr\n",
                        a("Name=bond0", "Kind=bond", "Mode=active-backup"),
                        a("Kind=dummy")))
                .file(f("/etc/systemd/network/20-bond0.network",
                        "[Match]\nName=bond0\n\n[Network]\nDHCP=yes\n",
                        a("Name=bond0", "Address=10.42.7.100/24", "DHCP=no"),
                        a("DHCP=yes", "Bond=bond0")))
                .file(f("/etc/systemd/network/20-eth1.network",
                        "[Match]\nName=eth1\n\n[Network]\n",
                        a("Name=eth1", "Bond=bond0"), a()))
                .file(f("/etc/systemd/network/20-eth2.network",
                        "[Match]\nName=eth2\n\n[Network]\n",
                        a("Name=eth2", "Bond=bond0"), a())));

        MISSIONS.add(new M("life", "life", "koa", "FIX THE SCRUBBER GATEWAY",
                "CO2 scrubber is routing through a gateway that does not exist. We have about two "
                        + "minutes of clean air buffer. Fix the gateway. Fast.",
                "FIX THE DEFAULT GATEWAY - LIFE SUPPORT CONSOLE",
                "The gateway must be 10.42.7.1. 10.42.9.254 goes nowhere. Two minutes. Go.",
                "Gateway corrected. CO2 levels falling. Everyone breathe.",
                "Airwarden", "Saved the air supply against the clock.")
                .limit(120)
                .file(f("/etc/systemd/network/10-scrubber.network",
                        "[Match]\nName=scrub0\n\n[Network]\nAddress=10.42.7.50/24\n"
                                + "Gateway=10.42.9.254\nDNS=10.42.9.254\n",
                        a("Gateway=10.42.7.1"),
                        a("10.42.9.254"))));

        MISSIONS.add(new M("fuel", "fuel", "bolt", "IDENTIFY THE VALVE CONTROLLER",
                "Identify the valve controller by its MAC and give it a fixed address. MAC matching "
                        + "selects an interface; it does not prevent spoofing or secure the network.",
                "PIN THE VALVE CONTROLLER BY MAC - FUEL CONSOLE",
                "Use MACAddress=02:1f:3c:aa:10:07 in [Match] and a static Address in 10.42.7.0/24.",
                "Controller pinned by MAC at 10.42.7.60/24. Valves opening.",
                "Valve Wrangler", "Selected the valve controller by MAC address.")
                .file(f("/etc/systemd/network/10-fuel0.network",
                        "[Match]\nName=fuel0\n\n[Network]\nDHCP=yes\n",
                        a("MACAddress=02:1f:3c:aa:10:07", "Address=10.42.7.60/24", "DHCP=no"),
                        a("DHCP=yes"))));

        MISSIONS.add(new M("science", "science", "mira", "ISOLATE THE LAB WITH VLAN 42",
                "The lab is broadcasting junk data into every corridor. I need a VLAN - id 42 - and the "
                        + "lab sealed inside it.",
                "CREATE VLAN 42 AND SEAL THE LAB - SCIENCE CONSOLE",
                "A .netdev with Kind=vlan and Id=42 in [VLAN], then VLAN=vlan-lab on the lab interface.",
                "VLAN 42 is up. The junk data can argue with itself.",
                "Lab Warden", "Sealed a noisy lab into its own VLAN.")
                .file(f("/etc/systemd/network/10-vlan-lab.netdev",
                        "[NetDev]\nName=vlan-lab\nKind=vlan\n",
                        a("Kind=vlan", "Name=vlan-lab", "Id=42"),
                        a()))
                .file(f("/etc/systemd/network/20-lab.network",
                        "[Match]\nName=lab0\n\n[Network]\nAddress=10.42.7.70/24\n",
                        a("VLAN=vlan-lab"),
                        a())));

        MISSIONS.add(new M("armory", "armory", "mara", "REBUILD THE SHIELD BRIDGE",
                "Shield grid will not arm. The bridge interface tying the weapon ports together is "
                        + "corrupted. Build br-shield.",
                "CREATE BR-SHIELD BRIDGE - ARMORY CONSOLE",
                "Kind=bridge in the netdev with Name=br-shield, and Bridge=br-shield on shield0.",
                "br-shield is up. Shields arming. If something looks at us, it blinks first.",
                "Shieldwright", "Rebuilt the bridge that arms the shields.")
                .file(f("/etc/systemd/network/10-br-shield.netdev",
                        "[NetDev]\nName=br0\nKind=bridge\n",
                        a("Name=br-shield", "Kind=bridge"),
                        a()))
                .file(f("/etc/systemd/network/20-shield.network",
                        "[Match]\nName=shield0\n\n[Network]\nAddress=10.42.7.80/24\n",
                        a("Bridge=br-shield"),
                        a())));

        MISSIONS.add(new M("server", "server", "nova2", "CLEAN THE CORE MATRIX",
                "This is my core. Two uplink files are fighting over the same interface and one of them "
                        + "has a gateway that goes nowhere. The first matching filename in lexical order wins. Find it and remove it.",
                "DELETE THE BAD UPLINK FILE, THEN RELOAD AND RECONFIGURE - CORE CONSOLE",
                "Inspect both files; remove the bad one, run networkctl reload, then networkctl reconfigure on the uplink to apply it.",
                "Matrix clean. The intended uplink configuration can now match first.",
                "Matrix Janitor", "Cleaned a conflicting pile of .network files.")
                .type(SHELL));

        MISSIONS.add(new M("bridge", "bridge", "vega", "PROVE YOUR NETWORK",
                "Before I hand you the comm array, prove you understand what you just fixed.",
                "ANSWER THE CAPTAIN'S QUESTIONS - BRIDGE",
                "Five questions about match, DHCP, bonds, reload and VLANs.",
                "Vega nods once. From her, that is a standing ovation.",
                "Bridge Cadet", "Passed the captain's network review.")
                .type(QUIZ));

        MISSIONS.add(new M("comm", "comm", "sol", "OPEN THE DISTRESS CHANNEL",
                "Last piece, Cadet. comm0 needs a default route and DNS, or our distress call is just a "
                        + "very expensive light show.",
                "CONFIGURE COMM0 FOR THE DISTRESS SIGNAL",
                "Set address, DNS and Domains in [Network]; add Destination=0.0.0.0/0 and Gateway=10.42.7.1 in [Route].",
                "Signal away. First try. Sol is going to tell everyone about this.",
                "Signal Bearer", "Sent the distress signal across the squall.")
                .file(f("/etc/systemd/network/10-comm0.network",
                        "[Match]\nName=comm0\n\n[Network]\nAddress=10.42.7.90/24\nDNS=10.42.9.254\n",
                        a("Address=10.42.7.90/24", "Gateway=10.42.7.1", "DNS=10.42.7.1",
                                "Domains=deep-range.fleet", "[Route]", "Destination=0.0.0.0/0"),
                        a("10.42.9.254"))));

        /* ----------------------------------------------------------- comics */
        COMICS.add(new Comic("intro")
                .p(0, 0xff4fd8ff, "CADET LOG. DAY ONE.",
                        "Deep Range Fleet, shakedown run 07. The courier ship ISV Lumen, six hours out of dock, "
                                + "carrying mail, medicine and one very nervous new network officer.")
                .p(1, 0xffa278ff, "CAPTAIN VEGA",
                        "Welcome aboard, Cadet. Keep the lights on, keep the doors opening - and do not touch "
                                + "the coffee machine's VLAN.")
                .p(2, 0xffffd166, "NOVA, SHIP AI",
                        "Your console is in Crew Quarters. I have taken the liberty of locking every door "
                                + "until you are dressed. And until the network works."));

        COMICS.add(new Comic("crew")
                .p(3, 0xff37e0c8, "BULKHEAD 0",
                        "The door sighs open. Down the corridor, a coffee machine beeps in gratitude.")
                .p(1, 0xffa278ff, "CADET",
                        "First fix of the day. I could get used to this.")
                .p(2, 0xff4fd8ff, "NOVA",
                        "Please do not. The last network officer got used to it. We found him in the vents."));

        COMICS.add(new Comic("level1")
                .p(4, 0xff7ef06a, "CANTEEN",
                        "Two systems online. The garden is green and the heart monitor beeps in the right direction.")
                .p(2, 0xff7ef06a, "PIP",
                        "The tomatoes say thank you. They also say the reactor smells like burnt toast.")
                .p(3, 0xffff7a45, "BULKHEAD 1",
                        "Level two unlocks. The corridor lights flicker orange, then hold."));

        COMICS.add(new Comic("level2")
                .p(5, 0xffff7a45, "REACTOR CORE",
                        "The bond holds. Two cables, one steady heartbeat of power.")
                .p(2, 0xff59f7ff, "KOA",
                        "CO2 down to 0.04 percent. I can breathe again. Literally.")
                .p(1, 0xffa278ff, "CADET",
                        "Nine minutes until the next emergency. I have started a timer."));

        COMICS.add(new Comic("level3")
                .p(2, 0xffffc857, "BOLT",
                        "Valves open. The controller matches its configuration by MAC; spoofing remains possible.")
                .p(2, 0xffb98cff, "MIRA",
                        "Lab traffic is sealed in VLAN 42. The junk data can argue with itself.")
                .p(3, 0xffff5d8f, "BULKHEAD 3",
                        "Level four opens. Beyond it: the armory, the core matrix, and a very large door."));

        COMICS.add(new Comic("level4")
                .p(2, 0xffff5d8f, "MARA",
                        "Shields up. If something out there looks at us, it will blink first.")
                .p(4, 0xff42f5a7, "CORE MATRIX",
                        "The core matrix is quiet. The intended uplink configuration matches first.")
                .p(3, 0xff4fd8ff, "BRIDGE DOOR",
                        "The bridge door opens. Captain Vega stands in front of a very large screen."));

        COMICS.add(new Comic("bridge")
                .p(2, 0xffffd166, "CAPTAIN VEGA",
                        "You have earned the comm array, Cadet. Bring us a voice.")
                .p(1, 0xffa278ff, "CADET",
                        "One route. One DNS server. One very long-distance call.")
                .p(6, 0xffffd166, "COMM ARRAY",
                        "The tower hums, waiting for a default route."));

        COMICS.add(new Comic("end")
                .p(7, 0xffffd166, "DISTRESS SIGNAL",
                        "The signal cuts through the graviton squall. Somewhere far away, a relay answers.")
                .p(2, 0xffffd166, "CAPTAIN VEGA",
                        "Fleet Command acknowledges. They are sending a tender... and a new ship.")
                .p(0, 0xff4fd8ff, "GALAXY MAP UPDATED",
                        "Next stop: the systemd ship. CHAPTER ONE COMPLETE."));

        MISSIONS.clear(); NPCS.clear(); COMICS.clear();
        String[] names = {"COMMAND BRIDGE", "SECURITY CONTROL", "ENGINE ROOM", "POWER DISTRIBUTION", "LIFE SUPPORT / MEDBAY", "NAVIGATION", "RESEARCH LAB", "SECURITY STORAGE", "SERVER BAY", "AUXILIARY COMMAND", "COMMUNICATIONS"};
        for (int i = 0; i < names.length; i++) {
            R r = ROOMS.get(i);
            ROOMS.set(i, new R(r.id, names[i], r.x1, r.y1, r.x2, r.y2, r.accent, r.npc));
        }
        for (int i = 0; i < DOORS.size(); i++) {
            D d = DOORS.get(i);
            String[] needs = i == 0 ? a("crew") : i == 2 ? a("reactor", "canteen")
                    : i == 5 ? a("reactor", "life", "canteen", "medbay") : a();
            String[] labels = {"BRIDGE", "SECURITY", "ENGINE ROOM", "POWER DISTRIBUTION", "LIFE SUPPORT", "NAVIGATION", "RESEARCH LAB", "SECURITY STORAGE", "SERVER BAY", "AUXILIARY COMMAND", "BULKHEAD 4", "BULKHEAD 3", "BULKHEAD 2", "BULKHEAD 1", "COMMUNICATIONS"};
            DOORS.set(i, new D(d.x1, d.y1, d.x2, d.y2, labels[i], needs.length > 0, needs));
        }
        NPCS.add(new N("nova", "NOVA", 1180, 4590, 0xff4fd8ff, "crew"));
        NPCS.add(new N("power", "NOVA", 430, 3130, 0xffff7a45, "reactor"));
        NPCS.add(new N("air", "NOVA", 1330, 3130, 0xff59f7ff, "life"));
        NPCS.add(new N("security", "NOVA", 470, 3830, 0xff7ef06a, "canteen"));
        NPCS.add(new N("engine", "NOVA", 1300, 3830, 0xff5fd0ff, "medbay"));
        NPCS.add(new N("nav", "NOVA", 430, 2430, 0xffffc857, "fuel"));
        MISSIONS.add(new M("crew", "crew", "nova", "C1-M01: The Last Working Computer",
                "NOVA: The Blackstar is drifting and several systems are down. Before touching the controls, inspect the services. On Linux, systemd manages units such as services; systemctl asks it about their state. This terminal is a simulation, not a real shell.",
                "List services and inspect power-grid.service",
                "Run these commands one at a time, pressing RUN after each:\n\nsystemctl list-units --type=service --all\n\nThis lists the simulated service units, including inactive ones. Then inspect the power grid:\n\nsystemctl status power-grid.service\n\nStatus reports its current state; it does not start it. Both inspections are required here. Type help for the supported commands; TAB completes commands and virtual paths.",
                "NOVA: The diagnostic list and power-grid status give us a map of the trouble. Power, oxygen and security can now be repaired in parallel. Inspection alone has not started them.", "Diagnostics", "Inspected critical services.").type(SHELL));
        MISSIONS.add(new M("reactor", "reactor", "power", "C1-M02: Let There Be Light",
                "NOVA: The dark corridors are getting old. The power grid is represented by a systemd service, a unit that manages a running program. Start it to bring the Blackstar's lights back.",
                "Start power-grid.service",
                "Run:\n\nsudo systemctl start power-grid.service\n\nStart requests that the service run now; it does not enable it at boot. sudo is accepted by this simulator but does not grant real privileges. Optional: systemctl status power-grid.service shows the simulated state.",
                "NOVA: The simulated power-grid service is running and the lights are back. Starting a service now is separate from enabling it for future boots.", "Power", "Restored power.").type(SHELL));
        MISSIONS.add(new M("life", "life", "air", "C1-M03: Running Out of Air",
                "LINUS: The air is getting thin. NOVA: Oxygen control needs to run now and be enabled for boot. In systemd, starting and enabling a service are different actions.",
                "Start and enable oxygen.service",
                "Run:\n\nsudo systemctl enable --now oxygen.service\n\nEnable configures a unit to start at boot; --now also starts it immediately. In this simulator it sets both flags, without simulating a reboot. Optional checks: systemctl is-active oxygen.service and systemctl is-enabled oxygen.service. Starting alone will not complete this mission.",
                "LINUS: Can I breathe again? NOVA: Oxygen is running and marked enabled in the simulator. On a real system, enable and start are separate; --now requests both.", "Air", "Stabilized the oxygen supply.").type(SHELL));
        MISSIONS.add(new M("canteen", "canteen", "security", "C1-M04: The Locked Door",
                "NOVA: The bulkhead service failed because its configuration is missing. The systemd journal records service messages; a backup can restore the virtual configuration so the door controller can run again.",
                "Restore the bulkhead configuration and start the service",
                "Optional diagnosis:\n\njournalctl -u bulkhead.service -n 30 --no-pager\n\nOn Linux, -u selects a unit, -n 30 requests recent entries and --no-pager prints directly. This simulator returns a fixed log and does not require reading it. Restore the backup and restart:\n\nsudo cp /var/backups/bulkhead.conf /etc/blackstar/bulkhead.conf\nsudo systemctl restart bulkhead.service\n\ncp copies source to destination; here only this virtual restore is supported. Restart applies the restored configuration to the simulated service.",
                "NOVA: The backup restored the missing virtual configuration and the bulkhead service is running. The journal helped identify the fault, but reading it was optional. The security door can now respond.", "Security", "Repaired the bulkhead.").type(SHELL));
        MISSIONS.add(new M("medbay", "medbay", "engine", "C1-M05: The Heart of the Blackstar",
                "NOVA: The engine controller needs the power grid. A systemd unit file describes how a service is managed. Its [Unit] section can declare dependencies and ordering, but those are not the same thing.",
                "Edit the engine unit, reload systemd and start the engine",
                "Tap EDITOR in this terminal. It opens the existing virtual engine.service file. Keep its existing [Service] and [Install] content unchanged. Under [Unit], add these two lines alongside Description:\n\nRequires=power-grid.service\nAfter=power-grid.service\n\nRequires pulls in a required unit; After orders startup when both units are starting. After alone does not pull it in. Tap Save, then run each command:\n\nsudo systemctl daemon-reload\nsudo systemctl restart engine.service\n\nReload makes systemd reread unit files; it does not restart the service. Restart applies the loaded unit. This simulator also requires the power grid already running and checks both lines and the original service settings.",
                "NOVA: The engine is running with the saved dependency and ordering loaded. Requires and After have distinct jobs; daemon-reload read the file, and restart applied it. The Blackstar has a heartbeat again.", "Engine", "Repaired the dependencies.").type(SHELL));
        MISSIONS.add(new M("fuel", "fuel", "nav", "C1-M06: Escape Vector",
                "NOVA: Power, oxygen, security and the engine are ready. A systemd target groups units for a desired state. Our flight.target is fictional, and this simulator guards flight until all four repairs are ready.",
                "Isolate flight.target",
                "Optional inspection:\n\nsystemctl list-dependencies flight.target\n\nThis displays the simulated target's dependencies; it is not required for completion. Then run:\n\nsudo systemctl isolate flight.target\n\nIn real systemd, isolate starts the selected target and stops other units not needed by it, subject to unit settings. This game's fictional target instead checks its four vital repairs and preserves those services; it is not a general model of isolate.",
                "NOVA: The simulator accepted flight.target after checking the four repairs and kept the vital services running. We have an escape vector and a handshake from GHOST-17. LINUS: Can we connect? NOVA: First, learn what an IP address is. Chapter 2 is not playable yet.", "Escape", "Made the Blackstar flight-ready.").type(SHELL));
        COMICS.add(new Comic("intro").p(0, 0xff4fd8ff, "NOVA", "Good morning, Commander. Good news: you are alive.").p(1, 0xffa278ff, "LINUS", "And the bad news?").p(2, 0xffffd166, "NOVA", "I recommend not wasting the time you have left on questions.").p(1, 0xffa278ff, "LINUS", "I'm a pirate, not a system administrator!").p(2, 0xff4fd8ff, "NOVA", "Today you are both. The job market is flexible. Welcome aboard the Blackstar."));
    }

    /* --------------------------------------------------------------- quiz */

    static final String[][] QUIZ_Q = {
            {"Which section decides WHICH interface a .network file applies to?",
                    "[Match]", "[Network]", "[Route]"},
            {"Which key asks networkd for a dynamic lease?",
                    "Address=dhcp", "DHCP=yes", "Gateway=auto"},
            {"A bond is created in which kind of file?",
                    "A .netdev file with Kind=bond", "A .network file with Bond=yes", "A .link file with Kind=bond"},
            {"After networkctl reload reads changed files, what applies them to an existing link?",
                    "networkctl status", "networkctl reconfigure LINK", "ip link reload"},
            {"Why put the lab in VLAN 42?",
                    "To give it a faster address", "To isolate its traffic from the rest of the ship",
                    "To make the lab files visible on the bridge"}
    };

    static final int[] QUIZ_A = {0, 1, 0, 1, 1};

    static final String[][] GALAXY = {
            {"BLACKSTAR", "Chapter 1: Dead Ship Rising", "PLAYABLE"},
            {"GHOST NETWORK", "Chapter 2", "UNAVAILABLE"},
            {"THE HIDDEN CARGO", "Chapter 3", "UNAVAILABLE"},
            {"FLEET OF SHADOWS", "Chapter 4", "UNAVAILABLE"},
            {"GHOST PROTOCOL", "Later chapters", "UNAVAILABLE"}
    };

    private GameData() {
    }
}
