package dev.minehoster;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ServerManager {
    private final Map<String, ManagedServer> servers = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "Minehoster-IO"); t.setDaemon(true); return t; });

    public void init() {
        try { Files.createDirectories(root()); Files.createDirectories(jres()); Files.createDirectories(serversRoot()); Files.createDirectories(root().resolve("downloads")); }
        catch (IOException e) { e.printStackTrace(); }
        reload();
    }
    public Path root() { return Minecraft.getInstance().gameDirectory.toPath().resolve("minehoster"); }
    public Path jres() { return root().resolve("jres"); }
    public Path serversRoot() { return root().resolve("servers"); }
    public void reload() {
        try (var stream = Files.list(serversRoot())) { stream.filter(Files::isDirectory).forEach(dir -> servers.computeIfAbsent(dir.getFileName().toString(), n -> new ManagedServer(n, dir))); }
        catch (IOException e) { e.printStackTrace(); }
    }
    public List<ManagedServer> all() { List<ManagedServer> list=new ArrayList<>(servers.values()); list.sort((a,b)->a.name().compareToIgnoreCase(b.name())); return Collections.unmodifiableList(list); }
    public ManagedServer create(String name) throws IOException {
        String safe=name.trim().replaceAll("[^a-zA-Z0-9._-]","_"); if(safe.isBlank())throw new IOException("Invalid server name");
        Path dir=serversRoot().resolve(safe).normalize(); if(!dir.getParent().equals(serversRoot()))throw new IOException("Invalid server path");
        Files.createDirectories(dir); return servers.computeIfAbsent(safe,n->new ManagedServer(n,dir));
    }
    public void tick(){ for(ManagedServer s:servers.values())s.refreshProcessState(); }
    public void shutdownAll(){ for(ManagedServer s:servers.values()){ if(s.isRunning()){ try{s.sendCommand("stop");}catch(Exception ignored){} } } }
    public void start(String name){
        ManagedServer s=servers.get(name); if(s==null||s.isRunning())return;
        io.submit(()->{ try{
            Path jar=s.serverJar(); if(!Files.exists(jar))throw new IOException("No server jar installed");
            Path java=JreManager.findJava(jres()); if(java==null)throw new IOException("No Java 21 JRE installed");
            Files.writeString(s.directory().resolve("eula.txt"),"eula=true\n",StandardCharsets.UTF_8);
            ProcessBuilder pb=new ProcessBuilder(java.toString(),"-jar",jar.getFileName().toString(),"nogui"); pb.directory(s.directory().toFile()); pb.redirectErrorStream(true);
            Process p=pb.start(); s.attach(p); io.submit(()->readLogs(s,p));
        }catch(Exception e){s.log("[Minehoster] Start failed: "+e.getMessage());s.clearProcess();} });
    }
    private void readLogs(ManagedServer s,Process p){
        try(BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)s.log(line);}
        catch(IOException e){s.log("[Minehoster] Log stream closed: "+e.getMessage());}
        finally{try{p.waitFor(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}s.log("[Minehoster] Server process stopped.");s.clearProcess();}
    }
    public void stop(String name){ ManagedServer s=servers.get(name); if(s==null||!s.isRunning())return; io.submit(()->{if(!s.sendCommand("stop")){Process p=s.process();if(p!=null)p.destroy();}}); }
    public void command(String name,String command){ManagedServer s=servers.get(name);if(s!=null&&s.isRunning()&&!command.isBlank())s.sendCommand(command);}

    public static final class ManagedServer {
        private final String name; private final Path directory; private volatile Process process; private final List<String> log=Collections.synchronizedList(new ArrayList<>());
        ManagedServer(String name,Path directory){this.name=name;this.directory=directory;}
        public String name(){return name;} public Path directory(){return directory;} public Path serverJar(){return directory.resolve("server.jar");}
        public boolean isRunning(){return process!=null&&process.isAlive();} public Process process(){return process;}
        void attach(Process p){process=p;log("[Minehoster] Server started.");} void clearProcess(){process=null;} void refreshProcessState(){if(process!=null&&!process.isAlive())process=null;}
        boolean sendCommand(String command){Process p=process;if(p==null||!p.isAlive())return false;try{PrintWriter w=new PrintWriter(new OutputStreamWriter(p.getOutputStream(),StandardCharsets.UTF_8),true);w.println(command);return true;}catch(Exception e){log("[Minehoster] Command failed: "+e.getMessage());return false;}}
        void log(String line){log.add(line);if(log.size()>2000)log.subList(0,500).clear();} public List<String> logs(){synchronized(log){return List.copyOf(log);}}
    }
}
