package com.fleetacademy.game;

import java.util.HashMap;
import java.util.Map;

public final class NetworkConfigValidatorTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    public static void main(String[] args) {
        Map<String, String> f = new HashMap<>();
        f.put("10-monitor.network", "[Match]\nName=med0\n[Network]\nGateway=10.42.7.1\n[Route]\nDestination=10.42.8.0/24\n");
        check(!NetworkConfigValidator.validate("medbay", f));
        f.put("10-monitor.network", f.get("10-monitor.network") + "Gateway=10.42.7.1\n");
        check(NetworkConfigValidator.validate("medbay", f));
        f.clear();
        f.put("10-bond0.netdev", "[NetDev]\nName=bond0\nKind=bond\n[Bond]\nMode=active-backup\n");
        f.put("20-bond0.network", "[Match]\nName=bond0\n[Network]\nAddress=10.42.7.100/24\nDHCP=no\nBond=bond0\n");
        f.put("20-eth1.network", "[Match]\nName=eth1\n[Network]\nBond=bond0\n");
        f.put("20-eth2.network", "[Match]\nName=eth2\n[Network]\nBond=bond0\n");
        check(!NetworkConfigValidator.validate("reactor", f));
        f.put("20-bond0.network", "[Match]\nName=bond0\n[Network]\nAddress=10.42.7.100/24\nDHCP=no\n");
        check(NetworkConfigValidator.validate("reactor", f));
        f.remove("20-eth2.network");
        check(!NetworkConfigValidator.validate("reactor", f));
        f.clear();
        f.put("10-vlan-lab.netdev", "[NetDev]\nName=vlan-lab\nKind=vlan\nId=42\n");
        f.put("20-lab.network", "[Match]\nName=lab0\n[Network]\nVLAN=vlan-lab\n");
        check(!NetworkConfigValidator.validate("science", f));
        f.put("10-vlan-lab.netdev", "[NetDev]\nName=vlan-lab\nKind=vlan\n[VLAN]\nId=42\n");
        check(NetworkConfigValidator.validate("science", f));
        f.clear();
        f.put("10-br-shield.netdev", "[NetDev]\nName=br-shield\nKind=bridge\n");
        f.put("20-shield.network", "[Match]\nName=shield0\n[Network]\nBridge=br-shield\n");
        check(NetworkConfigValidator.validate("armory", f));
        f.put("20-shield.network", "[Match]\nName=wrong\n[Network]\nBridge=br-shield\n");
        check(!NetworkConfigValidator.validate("armory", f));
        f.clear();
        f.put("10-comm0.network", "[Match]\nName=comm0\n[Network]\nAddress=10.42.7.90/24\nDNS=10.42.7.1\nDomains=deep-range.fleet\nGateway=10.42.7.1\n[Route]\nDestination=0.0.0.0/0\n");
        check(!NetworkConfigValidator.validate("comm", f));
        f.put("10-comm0.network", f.get("10-comm0.network") + "Gateway=10.42.7.1\n");
        check(NetworkConfigValidator.validate("comm", f));
        check(!NetworkConfigValidator.validate("life", new HashMap<String, String>()));
        System.out.println("NetworkConfigValidatorTest passed");
    }
}
