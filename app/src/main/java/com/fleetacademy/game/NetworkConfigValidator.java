package com.fleetacademy.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section-aware checks for the configuration missions. */
public final class NetworkConfigValidator {
    private NetworkConfigValidator() { }

    private static final class Section {
        final String name;
        final Map<String, List<String>> values = new LinkedHashMap<>();
        Section(String name) { this.name = name; }
        void add(String key, String value) {
            List<String> list = values.get(key);
            if (list == null) { list = new ArrayList<>(); values.put(key, list); }
            if (value.isEmpty()) list.clear();
            list.add(value);
        }
        boolean has(String key, String value) {
            List<String> list = values.get(key);
            return list != null && list.contains(value);
        }
        boolean last(String key, String value) {
            List<String> list = values.get(key);
            return list != null && !list.isEmpty() && value.equals(list.get(list.size() - 1));
        }
    }

    private static List<Section> parse(String text) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                current = new Section(line.substring(1, line.length() - 1).trim());
                sections.add(current);
            } else if (current != null) {
                int equals = line.indexOf('=');
                if (equals > 0) current.add(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
            }
        }
        return sections;
    }

    private static Section merged(List<Section> sections, String name) {
        Section result = new Section(name);
        for (Section section : sections) if (section.name.equals(name)) {
            for (Map.Entry<String, List<String>> entry : section.values.entrySet()) {
                for (String value : entry.getValue()) result.add(entry.getKey(), value);
            }
        }
        return result;
    }

    private static boolean route(List<Section> sections, String destination, String gateway) {
        for (Section section : sections) if (section.name.equals("Route")
                && section.last("Destination", destination) && section.last("Gateway", gateway)) return true;
        return false;
    }

    private static List<Section> file(Map<String, String> files, String name) {
        String text = files.get(name);
        if (text == null) text = files.get("/etc/systemd/network/" + name);
        return parse(text == null ? "" : text);
    }

    private static boolean match(List<Section> sections, String iface) {
        Section m = merged(sections, "Match");
        List<String> names = m.values.get("Name");
        return names != null && names.size() == 1 && names.get(0).equals(iface);
    }

    /** Validate a mission using a map keyed by full paths or basenames. */
    public static boolean validate(String missionId, Map<String, String> files) {
        if (missionId == null || files == null) return false;
        List<Section> s;
        Section n;
        switch (missionId) {
            case "crew":
                s = file(files, "10-eth0.network"); n = merged(s, "Network");
                return match(s, "eth0") && n.has("Address", "10.42.7.10/24")
                        && n.last("Gateway", "10.42.7.1") && n.has("DNS", "10.42.7.1") && n.last("DHCP", "no");
            case "canteen":
                s = file(files, "20-sensor0.network"); n = merged(s, "Network");
                return match(s, "sensor0") && n.has("Address", "10.42.7.40/24")
                        && !n.has("Address", "10.42.7.99/24") && n.last("DHCP", "no");
            case "medbay":
                s = file(files, "10-monitor.network");
                return match(s, "med0") && route(s, "10.42.8.0/24", "10.42.7.1");
            case "reactor":
                s = file(files, "10-bond0.netdev"); n = merged(s, "NetDev");
                if (!n.last("Name", "bond0") || !n.last("Kind", "bond")
                        || !merged(s, "Bond").last("Mode", "active-backup")) return false;
                s = file(files, "20-bond0.network"); n = merged(s, "Network");
                if (!match(s, "bond0") || n.values.containsKey("Bond")
                        || !n.has("Address", "10.42.7.100/24") || !n.last("DHCP", "no")) return false;
                for (String iface : new String[]{"eth1", "eth2"}) {
                    s = file(files, "20-" + iface + ".network");
                    if (!match(s, iface) || !merged(s, "Network").last("Bond", "bond0")) return false;
                }
                return true;
            case "life":
                s = file(files, "10-scrubber.network"); n = merged(s, "Network");
                return match(s, "scrub0") && n.last("Gateway", "10.42.7.1")
                        && !n.has("DNS", "10.42.9.254");
            case "fuel":
                s = file(files, "10-fuel0.network"); n = merged(s, "Network");
                Section m = merged(s, "Match");
                return m.last("MACAddress", "02:1f:3c:aa:10:07")
                        && (!m.values.containsKey("Name") || match(s, "fuel0"))
                        && n.has("Address", "10.42.7.60/24") && n.last("DHCP", "no");
            case "science":
                s = file(files, "10-vlan-lab.netdev"); n = merged(s, "NetDev");
                if (!n.last("Name", "vlan-lab") || !n.last("Kind", "vlan") || !merged(s, "VLAN").last("Id", "42")) return false;
                s = file(files, "20-lab.network");
                return match(s, "lab0") && merged(s, "Network").has("VLAN", "vlan-lab");
            case "armory":
                s = file(files, "10-br-shield.netdev"); n = merged(s, "NetDev");
                if (!n.last("Name", "br-shield") || !n.last("Kind", "bridge")) return false;
                s = file(files, "20-shield.network");
                return match(s, "shield0") && merged(s, "Network").last("Bridge", "br-shield");
            case "comm":
                s = file(files, "10-comm0.network"); n = merged(s, "Network");
                return match(s, "comm0") && n.has("Address", "10.42.7.90/24")
                        && n.has("DNS", "10.42.7.1") && !n.has("DNS", "10.42.9.254")
                        && n.has("Domains", "deep-range.fleet") && route(s, "0.0.0.0/0", "10.42.7.1");
            default: return false;
        }
    }
}
