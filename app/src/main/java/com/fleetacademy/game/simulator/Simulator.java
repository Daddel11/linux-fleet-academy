package com.fleetacademy.game.simulator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

public final class Simulator {
    private static final int MAX_INPUT = 8192, MAX_PATH = 4096, MAX_COMPONENT = 255, MAX_SNAPSHOT = 16000000, MAX_ITEMS = 100000;
    private static final String RECIPE = "/storage/recipes/soup.txt", RECIPE_CONTENT = "Space soup\n";
    public static final class Change {
        public final String type, target, detail;
        Change(String type, String target, String detail) { this.type = type; this.target = target; this.detail = detail; }
    }
    public static final class Event {
        public final String eventId, correlationId, type, target, detail;
        public final long worldRevision;
        Event(String id, String correlation, long revision, Change change) {
            eventId = id; correlationId = correlation; worldRevision = revision;
            type = change.type; target = change.target; detail = change.detail;
        }
    }
    public static final class Result {
        public final String stdout, stderr;
        public final int exitCode, durationTicks;
        public final long revision;
        public final List<Change> mutations;
        public final List<Event> domainEvents;
        Result(String out, String err, int code, long rev, List<Change> changes, List<Event> events) {
            stdout = out; stderr = err; exitCode = code; durationTicks = 1; revision = rev;
            mutations = Collections.unmodifiableList(new ArrayList<>(changes));
            domainEvents = Collections.unmodifiableList(new ArrayList<>(events));
        }
    }
    private static final class Node {
        boolean dir; String owner, group, content; int mode; long mtime;
        Node(boolean d, String o, String g, int m, String c, long t) { dir=d; owner=o; group=g; mode=m; content=c; mtime=t; }
        Node copy() { return new Node(dir, owner, group, mode, content, mtime); }
    }
    private static final class State {
        TreeMap<String, Node> nodes = new TreeMap<>();
        TreeMap<String, String> services = new TreeMap<>();
        ArrayList<String> journal = new ArrayList<>();
        String cwd = "/home/linus"; long revision, clock;
        State copy() {
            State s = new State(); for (Map.Entry<String,Node> e : nodes.entrySet()) s.nodes.put(e.getKey(),e.getValue().copy());
            s.services.putAll(services); s.journal.addAll(journal); s.cwd=cwd; s.revision=revision; s.clock=clock; return s;
        }
    }
    private static final class Failure extends Exception {
        Failure(String message) { super(message); }
    }
    private State state;
    public Simulator() { state = initial(); }
    private Simulator(State state) { this.state = state; }
    private static State initial() {
        State s = new State();
        add(s,"/",true,"root",0755,""); add(s,"/home",true,"root",0755,"");
        add(s,"/home/linus",true,"linus",0755,""); add(s,"/home/linus/notes",true,"linus",0755,"");
        add(s,"/home/linus/welcome.txt",false,"linus",0644,"Welcome aboard the Tux Fortuna!\n");
        add(s,"/storage",true,"linus",0755,""); add(s,"/storage/inventory.csv",false,"linus",0644,"item,quantity\n");
        add(s,"/storage/recipes",true,"linus",0755,""); add(s,RECIPE,false,"linus",0644,RECIPE_CONTENT);
        add(s,"/var",true,"root",0755,""); add(s,"/var/log",true,"root",0755,""); add(s,"/var/log/ship.log",false,"root",0644,"Ship systems online.\n");
        add(s,"/etc",true,"root",0755,""); add(s,"/etc/ship",true,"root",0755,""); add(s,"/etc/ship/services.conf",false,"root",0644,"Ship services\n");
        add(s,"/usr",true,"root",0755,""); add(s,"/usr/share",true,"root",0755,""); add(s,"/usr/share/manuals",true,"root",0755,"");
        for (String name : new String[]{"ship-lights.service","power-bus.service","storage-index.service","engine-cooling.service","engine-core.service","galley-printer.service"}) s.services.put(name, name.equals("ship-lights.service") || name.equals("power-bus.service") ? "active" : "inactive");
        return s;
    }
    private static void add(State s,String path,boolean dir,String owner,int mode,String content) { s.nodes.put(path,new Node(dir,owner,owner,mode,content,0)); }
    private static String parent(String p) { int i=p.lastIndexOf('/'); return i<=0?"/":p.substring(0,i); }
    private static String basename(String p) { return p.substring(p.lastIndexOf('/')+1); }
    private static int utfLength(String value) {
        int size=0; for(int i=0;i<value.length();i++) { int c=value.charAt(i); size+=c>=1&&c<=127?1:c<=2047?2:3; } return size;
    }
    private static String path(State s,String p) throws Failure { return path(s,p,false,false,null); }
    private static String path(State s,String p,boolean parents,boolean allowTrailingMissing,List<Change> changes) throws Failure {
        if(p.isEmpty()) throw new Failure("No such file or directory");
        if(utfLength(p)>MAX_PATH) throw new Failure("Path too long");
        String cur=p.startsWith("/")?"/":s.cwd; String[] parts=p.split("/",-1);
        for(int i=0;i<parts.length;i++) {
            String part=parts[i]; if(part.isEmpty()) continue;
            Node dir=lookup(s,cur); if(!dir.dir) throw new Failure("Not a directory"); if(!permitted(dir,1)) throw new Failure("Permission denied");
            if(part.equals(".")) continue;
            if(part.equals("..")) { cur=parent(cur); continue; }
            if(utfLength(part)>MAX_COMPONENT) throw new Failure("File name too long");
            String next=(cur.equals("/")?"":cur)+"/"+part;
            if(utfLength(next)>MAX_PATH) throw new Failure("Path too long");
            if(parents&&!s.nodes.containsKey(next)) { writableParent(s,next); add(s,next,true,"linus",0755,""); change(changes,"FsDirectoryCreated",next,"linus"); }
            cur=next;
        }
        if(p.endsWith("/")&&!cur.equals("/")) { Node n=s.nodes.get(cur); if(n==null&&!allowTrailingMissing) throw new Failure("No such file or directory"); if(n!=null&&!n.dir) throw new Failure("Not a directory"); }
        return cur;
    }
    private static boolean permitted(Node n,int bit) { return ((n.mode >> (n.owner.equals("linus")?6:n.group.equals("linus")?3:0)) & bit)!=0; }
    private static Node lookup(State s,String p) throws Failure {
        if (!p.equals("/")) {
            String cur=""; String[] parts=p.substring(1).split("/");
            for (int i=0;i<parts.length-1;i++) { cur+="/"+parts[i]; Node n=s.nodes.get(cur); if(n==null) throw new Failure("No such file or directory"); if(!n.dir) throw new Failure("Not a directory"); if(!permitted(n,1)) throw new Failure("Permission denied"); }
        }
        Node n=s.nodes.get(p); if(n==null) throw new Failure("No such file or directory"); return n;
    }
    private static void writableParent(State s,String p) throws Failure { Node n=lookup(s,parent(p)); if(!n.dir) throw new Failure("Not a directory"); if(!permitted(n,3) || !permitted(n,2) || !permitted(n,1)) throw new Failure("Permission denied"); }
    private static List<String> parse(String raw) throws Failure {
        if(raw.length()>MAX_INPUT || utfLength(raw)>MAX_INPUT) throw new Failure("Input too long");
        ArrayList<String> a=new ArrayList<>(); StringBuilder b=new StringBuilder(); char quote=0; boolean escaped=false, started=false;
        for(int i=0;i<raw.length();i++) { char c=raw.charAt(i);
            if(escaped) { b.append(c); escaped=false; started=true; continue; }
            if(c=='\\' && quote!='\'') { escaped=true; started=true; continue; }
            if(quote!=0) { if(c==quote) quote=0; else { if(quote=='"' && (c=='$'||c=='`')) throw new Failure("Unsupported shell expansion"); b.append(c); } started=true; continue; }
            if(c=='\''||c=='"') { quote=c; started=true; }
            else if(";|&<>$`()\n\r".indexOf(c)>=0) throw new Failure("Unsupported shell operator or expansion");
            else if(Character.isWhitespace(c)) { if(started) { a.add(b.toString()); b.setLength(0); started=false; } }
            else { b.append(c); started=true; }
        }
        if(quote!=0||escaped) throw new Failure("Unterminated quote or escape"); if(started) a.add(b.toString()); return a;
    }
    private static void count(List<String> a,int min,int max) throws Failure { if(a.size()<min||a.size()>max) throw new Failure("Invalid arguments"); }
    private static void change(List<Change> c,String type,String target,String detail) { c.add(new Change(type,target,detail)); }
    private static String mode(Node n) { StringBuilder b=new StringBuilder(n.dir?"d":"-"); for(int i=8;i>=0;i--) b.append((n.mode & (1<<i))==0?'-':"rwx".charAt((8-i)%3)); return b.toString(); }
    private static String display(String p,Node n,boolean l) { return l ? mode(n)+" 1 "+n.owner+" "+n.group+" "+n.content.getBytes(StandardCharsets.UTF_8).length+" "+basename(p)+"\n" : basename(p)+"\n"; }
    private static String dependency(State s,String unit) {
        if(unit.equals("engine-core.service") && !"active".equals(s.services.get("engine-cooling.service"))) return "engine-cooling.service";
        if((unit.equals("engine-cooling.service")||unit.equals("ship-lights.service")||unit.equals("galley-printer.service")) && !"active".equals(s.services.get("power-bus.service"))) return "power-bus.service";
        if(unit.equals("storage-index.service") && (!s.nodes.containsKey("/storage/robot") || !s.nodes.get("/storage/robot").dir)) return "/storage/robot";
        if(unit.equals("galley-printer.service") && (!s.nodes.containsKey("/storage/recipes/soup-backup.txt") || s.nodes.get("/storage/recipes/soup-backup.txt").dir || !s.nodes.get("/storage/recipes/soup-backup.txt").content.equals(RECIPE_CONTENT))) return "/storage/recipes/soup-backup.txt (correct recipe required)";
        return null;
    }
    private static void serviceChange(State s,List<Change> c,String unit,String next,String reason) {
        String old=s.services.put(unit,next); change(c,"ServiceStateChanged",unit,old+" -> "+next);
        s.journal.add(unit+": "+reason); change(c,"JournalEntryAdded",unit,reason);
    }
    private static void reconcile(State s,List<Change> c) {
        boolean changed; do { changed=false; for(String unit:s.services.keySet()) if(s.services.get(unit).equals("active")) { String dep=dependency(s,unit); if(dep!=null) { serviceChange(s,c,unit,"inactive","Stopped: dependency lost: "+dep); changed=true; } } } while(changed);
    }
    public synchronized Result execute(String raw) {
        State next=state.copy(); List<Change> changes=new ArrayList<>(); String out="",err=""; int code=0;
        try {
            List<String> a=parse(raw==null?"":raw); if(!a.isEmpty()) out=run(next,a,changes);
            if(!a.isEmpty() && a.get(0).equals("systemctl") && a.size()==3 && a.get(1).equals("start") && !changes.isEmpty() && changes.get(0).type.equals("JournalEntryAdded")) { err=out; out=""; code=1; }
            if(!changes.isEmpty()) reconcile(next,changes);
            if(!changes.isEmpty()) {
                if(state.revision==Long.MAX_VALUE || state.clock==Long.MAX_VALUE) throw new Failure("Simulation clock exhausted");
                next.revision=state.revision+1; next.clock=state.clock+1;
                try { encode(next); } catch(IOException e) { throw new Failure("State exceeds snapshot limits"); }
            }
        } catch(Failure e) { err=e.getMessage()+"\n"; code=1; changes.clear(); next=state.copy(); }
        if(!changes.isEmpty()) state=next;
        List<Event> events=new ArrayList<>(); String correlation="command-"+state.revision;
        for(int i=0;i<changes.size();i++) events.add(new Event(correlation+"-"+i,correlation,state.revision,changes.get(i)));
        return new Result(out,err,code,state.revision,changes,events);
    }
    private static String run(State s,List<String> a,List<Change> c) throws Failure {
        String cmd=a.get(0); StringBuilder out=new StringBuilder();
        if(Arrays.asList("cd","touch","cat","mkdir","cp","rm").contains(cmd)) for(int i=1;i<a.size();i++) if(a.get(i).startsWith("-") && !(i==1 && ((cmd.equals("mkdir")&&a.get(i).equals("-p"))||((cmd.equals("cp")||cmd.equals("rm"))&&a.get(i).equals("-r"))))) throw new Failure("Invalid option: "+a.get(i));
        switch(cmd) {
        case "pwd": count(a,1,1); return s.cwd+"\n";
        case "cd": { count(a,1,2); String p=path(s,a.size()==1?"/home/linus":a.get(1)); Node n=lookup(s,p); if(!n.dir) throw new Failure("Not a directory"); if(!permitted(n,1)||!permitted(n,4)) throw new Failure("Permission denied"); if(!p.equals(s.cwd)) { change(c,"ShellWorkingDirectoryChanged",p,s.cwd); s.cwd=p; } return ""; }
        case "ls": { boolean all=false,longForm=false; String arg=null; for(int i=1;i<a.size();i++) { String x=a.get(i); if(x.startsWith("-")) { if(x.length()==1) throw new Failure("Invalid option"); for(int j=1;j<x.length();j++) { if(x.charAt(j)=='a') all=true; else if(x.charAt(j)=='l') longForm=true; else throw new Failure("Invalid option: "+x); } } else { if(arg!=null) throw new Failure("Invalid arguments"); arg=x; } }
            String p=path(s,arg==null?".":arg); Node n=lookup(s,p); if(!n.dir) return display(p,n,longForm); if(!permitted(n,4)||!permitted(n,1)) throw new Failure("Permission denied");
            if(all) { if(longForm) { out.append(display(p.equals("/")?"/.":p+"/.",n,true)); out.append(display(p+"/..",s.nodes.get(parent(p)),true)); } else out.append(".\n..\n"); }
            for(Map.Entry<String,Node> e:s.nodes.entrySet()) if(!e.getKey().equals(p)&&parent(e.getKey()).equals(p)&&(all||!basename(e.getKey()).startsWith("."))) out.append(display(e.getKey(),e.getValue(),longForm)); return out.toString(); }
        case "mkdir": { boolean parents=a.size()>1&&a.get(1).equals("-p"); count(a,parents?3:2,parents?3:2); String p=path(s,a.get(parents?2:1),parents,true,c); if(!parents) { if(s.nodes.containsKey(p)) throw new Failure("File exists"); writableParent(s,p); add(s,p,true,"linus",0755,""); change(c,"FsDirectoryCreated",p,"linus"); }
            else { Node n=lookup(s,p); if(!n.dir) throw new Failure("Not a directory"); if(!permitted(n,1)) throw new Failure("Permission denied"); } return ""; }
        case "touch": { count(a,2,2); String p=path(s,a.get(1)); if(s.nodes.containsKey(p)) { Node n=lookup(s,p); if(!permitted(n,2)) throw new Failure("Permission denied"); n.mtime=s.clock+1; change(c,"FsTimestampChanged",p,Long.toString(n.mtime)); } else { writableParent(s,p); add(s,p,false,"linus",0644,""); s.nodes.get(p).mtime=s.clock+1; change(c,"FsFileCreated",p,"0"); } return ""; }
        case "cat": { count(a,2,2); Node n=lookup(s,path(s,a.get(1))); if(n.dir) throw new Failure("Is a directory"); if(!permitted(n,4)) throw new Failure("Permission denied"); return n.content; }
        case "cp": { boolean recursive=a.size()>1&&a.get(1).equals("-r"); count(a,recursive?4:3,recursive?4:3); String src=path(s,a.get(recursive?2:1)), dst=path(s,a.get(recursive?3:2)); Node n=lookup(s,src); if(n.dir&&!recursive) throw new Failure("Is a directory (use -r)"); if(src.equals("/")) throw new Failure("Cannot copy into itself"); if(s.nodes.containsKey(dst)&&lookup(s,dst).dir) dst=(dst.equals("/")?"":dst)+"/"+basename(src); if(dst.equals(src)||dst.startsWith(src+"/")) throw new Failure("Cannot copy into itself"); writableParent(s,dst); if(s.nodes.containsKey(dst)) { Node existing=lookup(s,dst); if(existing.dir||n.dir) throw new Failure("Destination exists"); if(!permitted(existing,2)) throw new Failure("Permission denied"); }
            if(dst.equals(RECIPE)||RECIPE.startsWith(dst+"/")) throw new Failure("Protected recovery recipe");
            ArrayList<String> paths=new ArrayList<>(); for(String p:s.nodes.keySet()) if(p.equals(src)||p.startsWith(src+"/")) paths.add(p);
            for(String p:paths) { Node item=lookup(s,p); if(!permitted(item,4)||(item.dir&&!permitted(item,1))) throw new Failure("Permission denied"); }
            for(String p:paths) { String target=dst+p.substring(src.length()); if(utfLength(target)>MAX_PATH) throw new Failure("Path too long"); Node copy=s.nodes.get(p).copy(); copy.owner="linus"; copy.group="linus"; copy.mtime=s.clock+1; s.nodes.put(target,copy); } change(c,"FsFileCopied",dst,src); return ""; }
        case "rm": { boolean recursive=a.size()>1&&a.get(1).equals("-r"); count(a,recursive?3:2,recursive?3:2); String p=path(s,a.get(recursive?2:1)); if(p.equals("/")) throw new Failure("Unsafe operation: cannot remove root"); if(p.equals(RECIPE)||RECIPE.startsWith(p+"/")) throw new Failure("Protected recovery recipe"); if(s.cwd.equals(p)||s.cwd.startsWith(p+"/")) throw new Failure("Cannot remove current working directory"); Node n=lookup(s,p); if(n.dir&&!recursive) throw new Failure("Is a directory (use -r)"); writableParent(s,p); ArrayList<String> paths=new ArrayList<>(); for(String x:s.nodes.keySet()) if(x.equals(p)||x.startsWith(p+"/")) { Node item=lookup(s,x); if(item.dir&&(!permitted(item,2)||!permitted(item,1))) throw new Failure("Permission denied"); paths.add(x); } for(String x:paths) s.nodes.remove(x); change(c,"FsNodeRemoved",p,""); return ""; }
        case "systemctl": { count(a,3,3); String action=a.get(1),unit=a.get(2); if(!s.services.containsKey(unit)) throw new Failure("Unit not found: "+unit); if(action.equals("status")) return unit+" - "+s.services.get(unit)+"\n"; if(!action.equals("start")&&!action.equals("stop")) throw new Failure("Unsupported systemctl action"); String target=action.equals("start")?"active":"inactive"; if(s.services.get(unit).equals(target)) return ""; if(action.equals("start")) { String dep=dependency(s,unit); if(dep!=null) { s.journal.add(unit+": Start failed: dependency missing: "+dep); change(c,"JournalEntryAdded",unit,"Start failed: dependency missing: "+dep); return "FAILED: missing dependency: "+dep+"\n"; } } serviceChange(s,c,unit,target,action.equals("start")?"Started":"Stopped"); return ""; }
        case "journalctl": { String unit=null; int limit=Integer.MAX_VALUE; for(int i=1;i<a.size();i++) { String opt=a.get(i); if((opt.equals("-u")||opt.equals("-n"))&&i+1<a.size()) { String val=a.get(++i); if(opt.equals("-u")) { if(unit!=null||!s.services.containsKey(val)) throw new Failure("Unknown or duplicate unit"); unit=val; } else { try { limit=Integer.parseInt(val); if(limit<0) throw new NumberFormatException(); } catch(NumberFormatException e) { throw new Failure("Invalid line count"); } } } else throw new Failure("Invalid option: "+opt); } ArrayList<String> lines=new ArrayList<>(); for(String line:s.journal) if(unit==null||line.startsWith(unit+":")) lines.add(line); for(int i=Math.max(0,lines.size()-limit);i<lines.size();i++) out.append(lines.get(i)).append('\n'); return out.toString(); }
        case "help": case "man": { count(a,cmd.equals("man")?2:1,2); String list="pwd ls cd mkdir touch cat cp rm systemctl journalctl help man"; if(a.size()==1) return list+"\n"; String topic=a.get(1); if(!Arrays.asList(list.split(" ")).contains(topic)) throw new Failure("No help for "+topic); String text; switch(topic) { case "ls": text="ls [-la] [path]: list virtual files"; break; case "mkdir": text="mkdir [-p] path: create virtual directories"; break; case "cp": text="cp [-r] source destination: copy virtual files"; break; case "rm": text="rm [-r] path: remove virtual files; root is protected"; break; case "systemctl": text="systemctl status|start|stop UNIT: manage simulated services"; break; case "journalctl": text="journalctl [-u UNIT] [-n N]: read simulated service logs"; break; default: text=topic+": simulated Linux command"; } return text+"\n"; }
        default: throw new Failure("Unsupported command: "+cmd);
        }
    }
    private static int encodedStringSize(String value) throws IOException {
        int size=utfLength(value); if(size>65535) throw new IOException("Snapshot string limit"); return size+2;
    }
    private static byte[] encode(State state) throws IOException {
        if(state.nodes.size()>MAX_ITEMS||state.journal.size()>MAX_ITEMS) throw new IOException("Snapshot item limit");
        long size=4+8+8+encodedStringSize(state.cwd)+4+4+4;
        for(Map.Entry<String,Node> e:state.nodes.entrySet()) { Node n=e.getValue(); size+=(long)encodedStringSize(e.getKey())+1+encodedStringSize(n.owner)+encodedStringSize(n.group)+4+encodedStringSize(n.content)+8; }
        for(Map.Entry<String,String> e:state.services.entrySet()) size+=(long)encodedStringSize(e.getKey())+encodedStringSize(e.getValue());
        for(String line:state.journal) size+=encodedStringSize(line);
        if(size>MAX_SNAPSHOT-16) throw new IOException("Snapshot size limit");
        ByteArrayOutputStream payload=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(payload); d.writeInt(1); d.writeLong(state.revision); d.writeLong(state.clock); d.writeUTF(state.cwd); d.writeInt(state.nodes.size()); for(Map.Entry<String,Node> e:state.nodes.entrySet()) { Node n=e.getValue(); d.writeUTF(e.getKey()); d.writeBoolean(n.dir); d.writeUTF(n.owner); d.writeUTF(n.group); d.writeInt(n.mode); d.writeUTF(n.content); d.writeLong(n.mtime); } d.writeInt(state.services.size()); for(Map.Entry<String,String> e:state.services.entrySet()) { d.writeUTF(e.getKey()); d.writeUTF(e.getValue()); } d.writeInt(state.journal.size()); for(String line:state.journal) d.writeUTF(line); d.flush(); byte[] bytes=payload.toByteArray(); if(bytes.length>MAX_SNAPSHOT-16) throw new IOException("Snapshot size limit"); CRC32 crc=new CRC32(); crc.update(bytes); ByteArrayOutputStream result=new ByteArrayOutputStream(); DataOutputStream envelope=new DataOutputStream(result); envelope.writeInt(0x4c535031); envelope.writeInt(bytes.length); envelope.write(bytes); envelope.writeLong(crc.getValue()); return result.toByteArray();
    }
    public synchronized byte[] save() { try { return encode(state); } catch(IOException e) { throw new IllegalStateException(e); } }
    public static Simulator load(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length>MAX_SNAPSHOT) throw new IOException("Invalid snapshot size"); DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes)); if(in.readInt()!=0x4c535031) throw new IOException("Invalid snapshot magic"); int length=in.readInt(); if(length<0||length!=bytes.length-16) throw new IOException("Invalid snapshot length"); byte[] data=new byte[length]; in.readFully(data); long expected=in.readLong(); CRC32 crc=new CRC32(); crc.update(data); if(crc.getValue()!=expected) throw new IOException("Snapshot checksum mismatch"); DataInputStream d=new DataInputStream(new ByteArrayInputStream(data)); if(d.readInt()!=1) throw new IOException("Unsupported snapshot version"); State s=new State(); s.revision=d.readLong(); s.clock=d.readLong(); s.cwd=d.readUTF(); int n=d.readInt(); if(n<1||n>MAX_ITEMS) throw new IOException("Invalid node count"); for(int i=0;i<n;i++) { String p=d.readUTF(); Node node=new Node(d.readBoolean(),d.readUTF(),d.readUTF(),d.readInt(),d.readUTF(),d.readLong()); if(s.nodes.put(p,node)!=null) throw new IOException("Duplicate node"); } n=d.readInt(); if(n!=6) throw new IOException("Invalid services"); for(int i=0;i<n;i++) if(s.services.put(d.readUTF(),d.readUTF())!=null) throw new IOException("Duplicate service"); n=d.readInt(); if(n<0||n>MAX_ITEMS) throw new IOException("Invalid journal"); for(int i=0;i<n;i++) s.journal.add(d.readUTF()); if(d.available()!=0||s.revision<0||s.clock<0) throw new IOException("Invalid snapshot");
        try {
            if(!s.nodes.containsKey("/")||!s.nodes.get("/").dir) throw new IOException("Invalid root");
            for(Map.Entry<String,Node> e:s.nodes.entrySet()) { String p=e.getKey(); Node node=e.getValue();
                if(!p.startsWith("/")||utfLength(p)>MAX_PATH||(!p.equals("/")&&(p.endsWith("/")||p.contains("//")||!s.nodes.containsKey(parent(p))||!s.nodes.get(parent(p)).dir))) throw new IOException("Invalid VFS tree");
                for(String part:p.substring(1).split("/")) if(part.equals(".")||part.equals("..")||utfLength(part)>MAX_COMPONENT) throw new IOException("Invalid path component");
                if(node.owner.isEmpty()||node.group.isEmpty()||node.mode<0||node.mode>0777||node.mtime<0||(node.dir&&!node.content.isEmpty())) throw new IOException("Invalid node metadata");
            }
            Node recipe=s.nodes.get(RECIPE); if(recipe==null||recipe.dir||!recipe.content.equals(RECIPE_CONTENT)) throw new IOException("Missing recovery recipe");
            if(!path(s,s.cwd).equals(s.cwd)||!lookup(s,s.cwd).dir||!permitted(lookup(s,s.cwd),1)) throw new IOException("Invalid cwd");
            if(!s.services.keySet().equals(initial().services.keySet())) throw new IOException("Invalid service registry");
            for(Map.Entry<String,String> e:s.services.entrySet()) { String value=e.getValue(); if(!value.equals("active")&&!value.equals("inactive")&&!value.equals("failed")) throw new IOException("Invalid service state"); if(value.equals("active")&&dependency(s,e.getKey())!=null) throw new IOException("Invalid service dependency: "+e.getKey()); }
        } catch(Failure e) { throw new IOException("Invalid snapshot",e); } return new Simulator(s);
    }
}
