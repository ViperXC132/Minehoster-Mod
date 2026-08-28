package dev.minehoster;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class MinehosterScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox, pluginUrlBox, commandBox;
    private ServerManager.ManagedServer selected;
    private String status = "Ready";
    private int logOffset;

    public MinehosterScreen(Screen parent) { super(Component.literal("Minehoster")); this.parent = parent; }

    @Override protected void init() {
        super.init();
        nameBox = addRenderableWidget(new EditBox(font, 20, 40, 190, 20, Component.literal("Server name")));
        nameBox.setHint(Component.literal("Server name"));
        pluginUrlBox = addRenderableWidget(new EditBox(font, 20, 102, 330, 20, Component.literal("Plugin .jar URL")));
        pluginUrlBox.setHint(Component.literal("Plugin .jar URL"));
        commandBox = addRenderableWidget(new EditBox(font, 365, height - 28, Math.max(80, width - 465), 20, Component.literal("Command")));
        commandBox.setHint(Component.literal("Command to send to server"));
        addRenderableWidget(Button.builder(Component.literal("Create"), b -> createServer()).bounds(215, 40, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Java 21"), b -> downloadJava()).bounds(20, 66, 85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vanilla"), b -> downloadVanilla()).bounds(110, 66, 85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Paper"), b -> downloadPaper()).bounds(200, 66, 85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Install Plugin"), b -> installPlugin()).bounds(20, 126, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Start"), b -> start()).bounds(255, 160, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> stop()).bounds(255, 184, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> MinehosterClient.SERVERS.reload()).bounds(155, 126, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(width - 100, 10, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> sendCommand()).bounds(width - 95, height - 28, 75, 20).build());
    }

    private void createServer() {
        try { selected = MinehosterClient.SERVERS.create(nameBox.getValue()); status = "Created " + selected.name(); }
        catch (Exception e) { status = "Create failed: " + e.getMessage(); }
    }
    private void downloadJava() { status = "Downloading Java..."; CompletableFuture.runAsync(() -> { try { JreManager.downloadJava21(MinehosterClient.SERVERS.jres(), msg -> status = msg); } catch (Exception e) { status = "Java download failed: " + e.getMessage(); } }); }
    private ServerManager.ManagedServer target() { if (selected != null) return selected; var list = MinehosterClient.SERVERS.all(); return list.isEmpty() ? null : list.get(0); }
    private void downloadVanilla() { ServerManager.ManagedServer s=target(); if(s==null){status="Create a server first";return;} selected=s; status="Downloading Vanilla..."; CompletableFuture.runAsync(()->{try{ServerDownloader.downloadVanilla(s.directory(),msg->status=msg);}catch(Exception e){status="Vanilla failed: "+e.getMessage();}}); }
    private void downloadPaper() { ServerManager.ManagedServer s=target(); if(s==null){status="Create a server first";return;} selected=s; status="Downloading Paper..."; CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPaper(s.directory(),msg->status=msg);}catch(Exception e){status="Paper failed: "+e.getMessage();}}); }
    private void installPlugin() { ServerManager.ManagedServer s=target(); if(s==null){status="Create a server first";return;} String url=pluginUrlBox.getValue().trim(); if(url.isBlank()){status="Enter a plugin .jar URL";return;} selected=s; status="Installing plugin..."; CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPlugin(s.directory(),url,msg->status=msg);}catch(Exception e){status="Plugin failed: "+e.getMessage();}}); }
    private void start() { ServerManager.ManagedServer s=target(); if(s==null){status="Create a server first";return;} selected=s; MinehosterClient.SERVERS.start(s.name()); status="Starting "+s.name()+"..."; }
    private void stop() { ServerManager.ManagedServer s=target(); if(s==null)return; MinehosterClient.SERVERS.stop(s.name()); status="Stopping "+s.name()+"..."; }
    private void sendCommand() { ServerManager.ManagedServer s=target(); if(s!=null){MinehosterClient.SERVERS.command(s.name(),commandBox.getValue());commandBox.setValue("");} }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        g.drawString(font, "Minehoster — Server Manager", 20, 15, 0xFFFFFF);
        g.drawString(font, status, 20, height - 48, 0xAAAAAA);
        g.drawString(font, "Servers", 365, 18, 0xFFFFFF);
        var servers = MinehosterClient.SERVERS.all();
        int y=42;
        for(var s:servers){ boolean active=target()==s; int color=active?0x55FF55:0xFFFFFF; g.drawString(font,(s.isRunning()?"● ":"○ ")+s.name(),365,y,color); if(mouseX>=350&&mouseX<=width-20&&mouseY>=y-3&&mouseY<=y+12)g.fill(350,y-4,width-20,y+13,0x33000000); y+=16; }
        var s=target();
        if(s!=null){
            g.drawString(font,"Selected: "+s.name()+(s.isRunning()?" (RUNNING)":" (STOPPED)"),20,160,0xFFFFFF);
            g.drawString(font,"Server jar: "+(java.nio.file.Files.exists(s.serverJar())?"installed":"missing"),20,176,0xAAAAAA);
            g.drawString(font,"Live console",20,208,0xFFFFFF);
            var logs=s.logs(); int maxLines=Math.max(1,(height-250)/10); int start=Math.max(0,logs.size()-maxLines-logOffset); int end=Math.min(logs.size(),start+maxLines); int ly=222;
            for(int i=start;i<end;i++){String line=logs.get(i);if(line.length()>95)line=line.substring(0,95);g.drawString(font,line,20,ly,0xDDDDDD);ly+=10;}
        } else g.drawString(font,"Create a server to get started.",20,160,0xAAAAAA);
        super.render(g,mouseX,mouseY,delta);
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button==0&&mouseX>=350&&mouseX<=width-20&&mouseY>=38&&mouseY<38+MinehosterClient.SERVERS.all().size()*16){int index=(int)((mouseY-38)/16);var list=MinehosterClient.SERVERS.all();if(index>=0&&index<list.size()){selected=list.get(index);logOffset=0;return true;}}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontalAmount,double verticalAmount){if(mouseX<350){logOffset=Math.max(0,logOffset-(int)verticalAmount);return true;}return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==256){minecraft.setScreen(parent);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
