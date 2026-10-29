package com.fleetacademy.game;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChapterOneGuidanceTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Set<String> done = new HashSet<>();
        SimShell shell = new SimShell(null, done);
        String[] ids = {"crew", "reactor", "life", "canteen", "medbay", "fuel"};
        check(GameData.MISSIONS.size() == ids.length, "six active missions");
        for (String id : ids) {
            GameData.M m = GameData.mission(id);
            check(m != null && GameData.available(id, done), "available: " + id);
            check(m.brief.length() > 100 && m.hint.length() > 150 && m.success.length() > 100,
                    "complete guidance: " + id);
            if (id.equals("medbay")) {
                check(m.hint.contains("EDITOR") && m.hint.contains("[Unit]")
                        && m.hint.contains("Requires=power-grid.service")
                        && m.hint.contains("After=power-grid.service"), "editor guidance");
                shell.editUnit(SimShell.INITIAL_UNIT.replace("[Unit]\n", "[Unit]\n"
                        + "Requires=power-grid.service\nAfter=power-grid.service\n"));
            }
            int commands = 0;
            for (String line : m.hint.split("\n")) {
                if (!line.matches("(sudo )?(systemctl|journalctl) [a-z-]+.*")
                        && !line.startsWith("sudo cp /")) continue;
                String output = shell.run(line);
                check(shell.exitCode == 0 || (id.equals("crew") && line.startsWith("systemctl status")),
                        id + ": " + line + " => " + output);
                commands++;
            }
            check(commands > 0 && shell.solved(id), "guided commands solve " + id);
            done.add(id);
        }
        check(shell.flight && shell.power && shell.oxygen && shell.bulkhead && shell.engine,
                "flight guard preserves vital services");
        check(GameData.mission("canteen").hint.contains("Optional")
                && GameData.mission("fuel").hint.contains("not required"), "optional diagnostics");
        check(Arrays.asList(ids).containsAll(done), "mission identities preserved");
        System.out.println("ChapterOneGuidanceTest: PASS");
    }
}
