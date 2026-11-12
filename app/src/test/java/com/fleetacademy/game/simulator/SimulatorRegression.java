package com.fleetacademy.game.simulator;

import java.util.Arrays;
import java.io.*;
import java.util.zip.CRC32;

public final class SimulatorRegression {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static Simulator.Result ok(Simulator s,String cmd) { Simulator.Result r=s.execute(cmd); check(r.exitCode==0,cmd+": "+r.stderr); return r; }
    private static Simulator.Result fail(Simulator s,String cmd) { Simulator.Result r=s.execute(cmd); check(r.exitCode!=0,cmd+" unexpectedly succeeded"); return r; }
    private static void unchangedFailure(Simulator s,String cmd) { byte[] before=s.save(); Simulator.Result result=fail(s,cmd); check(result.mutations.isEmpty()&&Arrays.equals(before,s.save()),"atomic failure: "+cmd.substring(0,Math.min(80,cmd.length()))); }
    private static byte[] editedSnapshot(byte[] snapshot,String unit,String modePath,String contentPath) throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(snapshot)); in.readInt(); int len=in.readInt(); byte[] payload=new byte[len]; in.readFully(payload);
        DataInputStream d=new DataInputStream(new ByteArrayInputStream(payload)); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream o=new DataOutputStream(bytes);
        o.writeInt(d.readInt()); o.writeLong(d.readLong()); o.writeLong(d.readLong()); o.writeUTF(d.readUTF()); int n=d.readInt(); o.writeInt(n);
        for(int i=0;i<n;i++) { String path=d.readUTF(); o.writeUTF(path); o.writeBoolean(d.readBoolean()); o.writeUTF(d.readUTF()); o.writeUTF(d.readUTF()); int mode=d.readInt(); o.writeInt(path.equals(modePath)?0:mode); String content=d.readUTF(); o.writeUTF(path.equals(contentPath)?"broken":content); o.writeLong(d.readLong()); }
        n=d.readInt(); o.writeInt(n); for(int i=0;i<n;i++) { String name=d.readUTF(),value=d.readUTF(); o.writeUTF(name); o.writeUTF(name.equals(unit)?"active":value); }
        n=d.readInt(); o.writeInt(n); for(int i=0;i<n;i++) o.writeUTF(d.readUTF()); o.flush(); byte[] data=bytes.toByteArray(); CRC32 crc=new CRC32(); crc.update(data); ByteArrayOutputStream result=new ByteArrayOutputStream(); DataOutputStream envelope=new DataOutputStream(result); envelope.writeInt(0x4c535031); envelope.writeInt(data.length); envelope.write(data); envelope.writeLong(crc.getValue()); return result.toByteArray();
    }
    private static void rejects(byte[] bytes,String message) throws Exception { boolean rejected=false; try { Simulator.load(bytes); } catch(IOException e) { rejected=true; } check(rejected,message); }
    public static void main(String[] args) throws Exception {
        Simulator s=new Simulator();
        check(ok(s,"pwd").stdout.equals("/home/linus\n"),"initial cwd");
        ok(s,"cd /storage"); Simulator.Result created=ok(s,"mkdir robot");
        check(created.mutations.size()==1&&created.domainEvents.size()==1&&created.domainEvents.get(0).worldRevision==created.revision,"commit event");
        long revision=created.revision; Simulator.Result duplicate=fail(s,"mkdir /storage/robot");
        check(duplicate.revision==revision&&duplicate.mutations.isEmpty()&&duplicate.domainEvents.isEmpty(),"duplicate no mutation");
        check(ok(s,"ls /storage").stdout.contains("robot\n"),"alternative relative path");
        ok(s,"mkdir -p 'space dir/sub'"); ok(s,"touch space\\ dir/sub/.hidden");
        check(!ok(s,"ls 'space dir/sub'").stdout.contains("hidden"),"hidden omitted");
        check(ok(s,"ls -la 'space dir/sub'").stdout.contains(".hidden"),"hidden shown");
        ok(s,"cp -r 'space dir' copy"); check(ok(s,"ls -a copy/sub").stdout.contains(".hidden"),"recursive copy");
        byte[] before=s.save(); fail(s,"mkdir -p /storage/new/../../etc/nope/child"); check(Arrays.equals(before,s.save()),"atomic mkdir failure");
        before=s.save(); fail(s,"cp -r /storage /storage/copy/nested"); check(Arrays.equals(before,s.save()),"atomic recursive copy failure");
        fail(s,"touch /etc/ship/new"); fail(s,"rm -r /"); fail(s,"pwd; ls"); fail(s,"echo $(pwd)"); fail(s,"ls -z"); fail(s,"mkdir 'unterminated");
        ok(s,"touch 'literal;name'"); check(ok(s,"ls").stdout.contains("literal;name"),"quoted operator literal");
        Simulator.Result failedStart=fail(s,"systemctl start engine-core.service");
        check(failedStart.stderr.contains("engine-cooling.service"),"missing dependency diagnostic");
        check(ok(s,"journalctl -u engine-core.service").stdout.contains("Start failed"),"failure journal");
        ok(s,"systemctl start engine-cooling.service"); ok(s,"systemctl start engine-core.service");
        revision=ok(s,"systemctl start engine-core.service").revision; check(ok(s,"systemctl start engine-core.service").revision==revision,"idempotent start");
        ok(s,"systemctl stop engine-cooling.service"); check(ok(s,"systemctl status engine-core.service").stdout.contains("inactive"),"dependency loss");
        ok(s,"cp /storage/recipes/soup.txt /storage/recipes/soup-backup.txt"); ok(s,"systemctl start galley-printer.service");
        ok(s,"rm /storage/recipes/soup-backup.txt"); check(ok(s,"systemctl status galley-printer.service").stdout.contains("inactive"),"file dependency loss");
        check(ok(s,"journalctl -n 1").stdout.contains("dependency lost"),"journal last entry");
        ok(s,"systemctl start storage-index.service"); byte[] saved=s.save(); Simulator loaded=Simulator.load(saved);
        check(Arrays.equals(saved,loaded.save()),"byte snapshot roundtrip"); check(ok(loaded,"pwd").stdout.equals("/storage\n"),"cwd restored"); check(ok(loaded,"systemctl status storage-index.service").stdout.contains("active"),"service restored");
        saved[saved.length/2]^=1; boolean rejected=false; try { Simulator.load(saved); } catch(java.io.IOException e) { rejected=true; } check(rejected,"corruption rejected");
        ok(s,"rm -r copy"); check(!ok(s,"ls").stdout.contains("copy\n"),"recursive deletion");
        check(ok(s,"help mkdir").stdout.contains("mkdir"),"help"); check(ok(s,"man systemctl").stdout.contains("simulated"),"man");
        Simulator paths=new Simulator();
        unchangedFailure(paths,"cat /storage/missing/../inventory.csv");
        unchangedFailure(paths,"touch /storage/inventory.csv/../new");
        unchangedFailure(paths,"cat /storage/inventory.csv/");
        unchangedFailure(paths,"touch /storage/newfile/");
        unchangedFailure(paths,"mkdir /storage/missing/../new");
        ok(paths,"mkdir -p /storage/created/../other"); check(ok(paths,"ls /storage").stdout.contains("created\n"),"mkdir -p resolves in order");
        unchangedFailure(paths,"mkdir -p /storage/temp/../../etc/nope/child");
        unchangedFailure(paths,"rm -r /storage/recipes"); unchangedFailure(paths,"rm /storage/recipes/soup.txt");
        unchangedFailure(paths,"cp /storage/inventory.csv /storage/recipes/soup.txt");
        ok(paths,"touch /storage/recipes/soup-backup.txt"); fail(paths,"systemctl start galley-printer.service");
        ok(paths,"cp /storage/recipes/soup.txt /storage/recipes/soup-backup.txt"); ok(paths,"systemctl start galley-printer.service");
        StringBuilder huge=new StringBuilder(); for(int i=0;i<70000;i++) huge.append('x'); unchangedFailure(paths,"touch /storage/"+huge);
        check(Arrays.equals(paths.save(),Simulator.load(paths.save()).save()),"state remains saveable");
        Simulator initial=new Simulator(); rejects(editedSnapshot(initial.save(),"storage-index.service",null,null),"invalid storage dependency snapshot"); rejects(editedSnapshot(initial.save(),"engine-core.service",null,null),"invalid cooling dependency snapshot"); rejects(editedSnapshot(initial.save(),"galley-printer.service",null,null),"invalid recipe dependency snapshot");
        rejects(editedSnapshot(initial.save(),null,null,"/storage/recipes/soup.txt"),"invalid recovery recipe snapshot");
        Simulator locked=Simulator.load(editedSnapshot(initial.save(),null,"/storage/recipes",null));
        unchangedFailure(locked,"cat /storage/recipes/../inventory.csv"); unchangedFailure(locked,"mkdir -p /storage/recipes/../nope");
        StringBuilder component=new StringBuilder(); for(int i=0;i<255;i++) component.append('a'); ok(paths,"touch /storage/"+component); component.append('a'); unchangedFailure(paths,"touch /storage/"+component);
        StringBuilder unicode=new StringBuilder(); for(int i=0;i<86;i++) unicode.append('\u0800'); unchangedFailure(paths,"touch /storage/"+unicode);
        check(Arrays.equals(paths.save(),Simulator.load(paths.save()).save()),"bounded unicode state roundtrip");
        Simulator copies=new Simulator();
        for(String cmd:new String[]{"cp -r / /storage", "cp -r / /storage/new", "cp -r / /", "cp -r /. /storage/.", "cp -r / /home/linus/../linus"}) {
            byte[] snapshot=copies.save(); long beforeRevision=ok(copies,"pwd").revision; Simulator.Result result=fail(copies,cmd);
            check(result.revision==beforeRevision&&result.mutations.isEmpty()&&result.domainEvents.isEmpty(),"root copy has no effects: "+cmd);
            check(Arrays.equals(snapshot,copies.save())&&Arrays.equals(snapshot,Simulator.load(copies.save()).save()),"root copy snapshot unchanged: "+cmd);
        }
        ok(copies,"mkdir -p /storage/source/nested"); ok(copies,"touch /storage/source/nested/file");
        unchangedFailure(copies,"cp -r /storage/source /storage/source/nested/new");
        unchangedFailure(copies,"cp -r /storage/source /storage/source");
        ok(copies,"cp -r /storage/source /storage/source2");
        check(ok(copies,"ls /storage/source2/nested").stdout.contains("file\n"),"sibling prefix copy succeeds");
        byte[] copied=copies.save(); check(Arrays.equals(copied,Simulator.load(copied).save()),"valid recursive copy roundtrip");
        System.out.println("SimulatorRegression: "+checks+" checks passed");
    }
}
