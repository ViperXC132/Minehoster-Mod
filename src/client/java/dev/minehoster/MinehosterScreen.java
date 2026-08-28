package dev.minehoster;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Polished hosting-panel UI. DrawContext only; no raw GL/framebuffers. */
public final class MinehosterScreen extends Screen {
    private static final int BG=0xFF0B0E13,PANEL=0xFF12161D,PANEL2=0xFF171C24,BORDER=0xFF2A313D,TEXT=0xFFF2F4F7,MUTED=0xFF929AA8,GREEN=0xFF55E86B,GREEN_DARK=0xFF16351D,RED=0xFFFF5D5D,BLUE=0xFF65A9FF,PURPLE=0xFFB88CFF;
    private final Screen parent;
    private TextFieldWidget nameBox,pluginUrlBox,commandBox;
    private ServerManager.ManagedServer selected;
    private volatile String status="Ready";
    private int logOffset,nav;

    public MinehosterScreen(Screen parent){super(Text.literal("Minehoster"));this.parent=parent;}
    @Override protected void init(){
        super.init(); int mx=mainX();
        nameBox=addDrawableChild(new TextFieldWidget(textRenderer,mx,43,170,20,Text.literal("Server name")));
        nameBox.setPlaceholder(Text.literal("New server name")); nameBox.setDrawsBackground(false);
        pluginUrlBox=addDrawableChild(new TextFieldWidget(textRenderer,rightX()+10,369,120,20,Text.literal("Plugin URL")));
        pluginUrlBox.setPlaceholder(Text.literal("Plugin .jar URL")); pluginUrlBox.setDrawsBackground(false);
        commandBox=addDrawableChild(new TextFieldWidget(textRenderer,mx+22,height-55,Math.max(120,width-mx-310),20,Text.literal("Command")));
        commandBox.setPlaceholder(Text.literal("Type a command and press Enter")); commandBox.setDrawsBackground(false);
        var list=MinehosterClient.SERVERS.all(); if(selected==null&&!list.isEmpty())selected=list.get(0);
    }
    private int mainX(){return 190;} private int rightX(){return width-230;} private int consoleW(){return rightX()-mainX()-14;}
    private ServerManager.ManagedServer target(){if(selected!=null)return selected;var l=MinehosterClient.SERVERS.all();return l.isEmpty()?null:l.get(0);}
    private static String err(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void createServer(){try{selected=MinehosterClient.SERVERS.create(nameBox.getText());nameBox.setText("");status="Created "+selected.name();}catch(Exception e){status="Create failed: "+err(e);}}
    private void downloadJava(){status="Downloading Java 21...";CompletableFuture.runAsync(()->{try{JreManager.downloadJava21(MinehosterClient.SERVERS.jres(),m->status=m);}catch(Exception e){status="Java download failed: "+err(e);}});}
    private void downloadVanilla(){var s=target();if(s==null){status="Create a server first";return;}selected=s;status="Downloading Vanilla...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadVanilla(s.directory(),m->status=m);}catch(Exception e){status="Vanilla failed: "+err(e);}});}
    private void downloadPaper(){var s=target();if(s==null){status="Create a server first";return;}selected=s;status="Downloading Paper...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPaper(s.directory(),m->status=m);}catch(Exception e){status="Paper failed: "+err(e);}});}
    private void installPlugin(){var s=target();if(s==null){status="Create a server first";return;}String u=pluginUrlBox.getText().trim();if(u.isBlank()){status="Enter a plugin .jar URL";return;}status="Installing plugin...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPlugin(s.directory(),u,m->status=m);pluginUrlBox.setText("");}catch(Exception e){status="Plugin failed: "+err(e);}});}
    private void start(){var s=target();if(s==null){status="Create a server first";return;}MinehosterClient.SERVERS.start(s.name());status="Starting "+s.name()+"...";}
    private void stop(){var s=target();if(s==null)return;MinehosterClient.SERVERS.stop(s.name());status="Stopping "+s.name()+"...";}
    private void sendCommand(){var s=target();if(s!=null&&!commandBox.getText().isBlank()){MinehosterClient.SERVERS.command(s.name(),commandBox.getText());commandBox.setText("");}}
    private void refresh(){MinehosterClient.SERVERS.reload();var l=MinehosterClient.SERVERS.all();if(selected==null&&!l.isEmpty())selected=l.get(0);status="Server list refreshed";}

    private void panel(DrawContext c,int x,int y,int w,int h){c.fill(x,y,x+w,y+h,PANEL);c.fill(x,y,x+w,y+1,BORDER);c.fill(x,y+h-1,x+w,y+h,BORDER);c.fill(x,y,x+1,y+h,BORDER);c.fill(x+w-1,y,x+w,y+h,BORDER);}
    private void button(DrawContext c,String s,int x,int y,int w,int h,int accent,boolean filled,int mx,int my){boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+h;int b=filled?accent:(hover?0xFF222A35:PANEL2);c.fill(x,y,x+w,y+h,b);c.fill(x,y,x+w,y+1,hover?accent:BORDER);c.fill(x,y+h-1,x+w,y+h,BORDER);c.drawText(textRenderer,s,x+(w-textRenderer.getWidth(s))/2,y+(h-8)/2,filled?0xFF08100A:TEXT,true);}
    private void navItem(DrawContext c,String title,String sub,int y,boolean active,int mx,int my){int x=14,w=148,h=42;boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+h;if(active){c.fill(x,y,x+w,y+h,GREEN_DARK);c.fill(x,y,x+2,y+h,GREEN);}else if(hover)c.fill(x,y,x+w,y+h,PANEL2);c.drawText(textRenderer,title,x+38,y+8,active?GREEN:TEXT,true);c.drawText(textRenderer,sub,x+38,y+23,MUTED,false);}
    private void info(DrawContext c,String a,String b,int x,int y){c.drawText(textRenderer,a,x+14,y,MUTED,false);c.drawText(textRenderer,b,x+202-textRenderer.getWidth(b),y,TEXT,false);}
    private String type(ServerManager.ManagedServer s){if(java.nio.file.Files.exists(s.directory().resolve("paper.jar")))return "Paper";if(java.nio.file.Files.exists(s.directory().resolve("vanilla.jar")))return "Vanilla";return java.nio.file.Files.exists(s.serverJar())?"Minecraft Server":"Server";}

    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,BG); c.fill(0,0,176,height,0xFF0D1117);c.fill(175,0,176,height,BORDER);
        c.fill(28,25,54,51,0xFF4AAE57);c.fill(34,31,48,45,0xFF17201A);c.drawText(textRenderer,"MINEHOSTER",64,27,TEXT,true);c.drawText(textRenderer,"v1.0.0",64,41,MUTED,false);
        navItem(c,"Servers","Manage your servers",78,nav==0,mouseX,mouseY);navItem(c,"JREs","Manage Java runtimes",126,nav==1,mouseX,mouseY);navItem(c,"Downloads","JARs, plugins & more",174,nav==2,mouseX,mouseY);navItem(c,"Settings","Minehoster settings",222,nav==3,mouseX,mouseY);navItem(c,"About","About Minehoster",270,nav==4,mouseX,mouseY);
        c.drawText(textRenderer,"Storage",24,height-72,MUTED,false);c.fill(24,height-54,152,height-48,PANEL2);c.fill(24,height-54,72,height-48,GREEN);c.drawText(textRenderer,"minehoster",24,height-37,TEXT,false);c.drawText(textRenderer,"Local server manager",24,height-22,MUTED,false);
        int mx=mainX(),rx=rightX();var s=target();c.drawText(textRenderer,"Servers",mx,18,MUTED,false);c.drawText(textRenderer,">",mx+48,18,BORDER,false);c.drawText(textRenderer,s==null?"Dashboard":s.name(),mx+64,18,TEXT,true);if(s!=null){c.fill(mx+170,19,mx+178,27,s.isRunning()?GREEN:MUTED);c.drawText(textRenderer,s.isRunning()?"Running":"Stopped",mx+184,18,s.isRunning()?GREEN:MUTED,false);}button(c,"X",width-44,13,28,25,RED,false,mouseX,mouseY);
        // New server controls at the top, matching the hosting-panel mockup.
        c.fill(mx,40,mx+170,65,0xFF0C1015);button(c,"Create",mx+180,40,72,25,GREEN,true,mouseX,mouseY);c.drawText(textRenderer,"Server name",mx+7,47,MUTED,false);
        // Overview
        panel(c,mx,72,rx-mx-14,108);if(s==null){c.drawText(textRenderer,"No servers yet",mx+22,93,TEXT,true);c.drawText(textRenderer,"Create a server above to get started.",mx+22,113,MUTED,false);}else{c.fill(mx+20,91,mx+72,143,0xFF25331E);c.fill(mx+27,98,mx+65,136,0xFF3B7C32);c.drawText(textRenderer,s.name(),mx+88,91,TEXT,true);c.drawText(textRenderer,type(s),mx+88,112,MUTED,false);c.drawText(textRenderer,s.directory().toString(),mx+88,130,MUTED,false);c.drawText(textRenderer,"JAR",mx+88,149,BLUE,true);boolean jar=java.nio.file.Files.exists(s.serverJar());c.drawText(textRenderer,jar?"Installed":"Missing",mx+119,149,jar?GREEN:RED,false);c.drawText(textRenderer,"Process",mx+190,149,MUTED,false);c.drawText(textRenderer,s.isRunning()?"Online":"Offline",mx+237,149,s.isRunning()?GREEN:MUTED,false);} 
        int statX=rx-205;c.fill(statX,84,rx-26,168,PANEL2);c.drawText(textRenderer,"Status",statX+14,96,MUTED,false);c.drawText(textRenderer,s!=null&&s.isRunning()?"ONLINE":"OFFLINE",statX+14,113,s!=null&&s.isRunning()?GREEN:MUTED,true);c.drawText(textRenderer,"JRE",statX+14,136,MUTED,false);c.drawText(textRenderer,"Java 21",statX+55,136,TEXT,false);c.drawText(textRenderer,"Console",statX+14,157,MUTED,false);c.drawText(textRenderer,s==null?"0 lines":s.logs().size()+" lines",statX+62,157,TEXT,false);
        button(c,"▶  Start",mx,180,94,27,GREEN,true,mouseX,mouseY);button(c,"■  Stop",mx+101,180,94,27,RED,false,mouseX,mouseY);button(c,"↻  Restart",mx+202,180,94,27,BLUE,false,mouseX,mouseY);button(c,"↯  Kill",mx+303,180,80,27,RED,false,mouseX,mouseY);
        // Console
        int cy=218,ch=Math.max(150,height-322),cw=consoleW();panel(c,mx,cy,cw,ch);c.drawText(textRenderer,"▣  Console",mx+14,cy+12,TEXT,true);c.drawText(textRenderer,"LIVE",mx+88,cy+12,s!=null&&s.isRunning()?GREEN:MUTED,true);button(c,"Clear",mx+cw-76,cy+7,60,20,MUTED,false,mouseX,mouseY);int top=cy+34,bottom=cy+ch-43;c.fill(mx+10,top,mx+cw-10,bottom,0xFF090C10);if(s!=null){List<String> logs=s.logs();int max=Math.max(1,(bottom-top-8)/10);int start=Math.max(0,logs.size()-max-logOffset),end=Math.min(logs.size(),start+max),logY=top+8;for(int i=start;i<end;i++){String line=logs.get(i);int chars=Math.max(20,(cw-32)/6);if(line.length()>chars)line=line.substring(0,chars-1)+"…";int col=line.contains("ERROR")||line.contains("Exception")?RED:(line.contains("Done")||line.contains("ready")?GREEN:0xFFD1D6DE);c.drawText(textRenderer,line,mx+16,logY,col,false);logY+=10;}}else c.drawText(textRenderer,"No server selected. Create one to see live output.",mx+18,top+18,MUTED,false);int cmdY=cy+ch-34;c.fill(mx+10,cmdY,mx+cw-78,cmdY+27,0xFF0C1015);c.drawText(textRenderer,">",mx+18,cmdY+9,GREEN,true);button(c,"Send",mx+cw-70,cmdY,60,27,GREEN,true,mouseX,mouseY);
        // Controls
        panel(c,rx,180,216,150);c.drawText(textRenderer,"Server Controls",rx+14,194,GREEN,true);button(c,"Settings",rx+12,212,91,31,PURPLE,false,mouseX,mouseY);button(c,"Files",rx+108,212,91,31,BLUE,false,mouseX,mouseY);button(c,"Plugins",rx+12,249,91,31,PURPLE,false,mouseX,mouseY);button(c,"Worlds",rx+108,249,91,31,BLUE,false,mouseX,mouseY);button(c,"Java 21",rx+12,286,91,31,GREEN,false,mouseX,mouseY);button(c,"Downloads",rx+108,286,91,31,BLUE,false,mouseX,mouseY);
        c.fill(rx,338,rx+216,398,PANEL);c.drawText(textRenderer,"Install plugin",rx+12,346,MUTED,false);c.fill(rx+10,364,rx+132,391,0xFF0C1015);button(c,"Install",rx+140,364,66,27,GREEN,true,mouseX,mouseY);
        int iy=408;panel(c,rx,iy,216,126);c.drawText(textRenderer,"Server Info",rx+14,iy+13,TEXT,true);info(c,"Server Name",s==null?"—":s.name(),rx,iy+34);info(c,"Type",s==null?"—":type(s),rx,iy+51);info(c,"Server JAR",s!=null&&java.nio.file.Files.exists(s.serverJar())?"Installed":"Missing",rx,iy+68);info(c,"JRE",JreManager.findJava(MinehosterClient.SERVERS.jres())==null?"Not installed":"Java 21",rx,iy+85);info(c,"Status",s!=null&&s.isRunning()?"Running":"Stopped",rx,iy+102);
        // Server list
        int listY=height-116;panel(c,mx,listY,width-mx-14,96);c.drawText(textRenderer,"Your Servers",mx+14,listY+12,TEXT,true);button(c,"↻ Refresh",width-120,listY+7,90,22,MUTED,false,mouseX,mouseY);button(c,"+ Create Server",width-220,listY+7,92,22,GREEN,true,mouseX,mouseY);var servers=MinehosterClient.SERVERS.all();int row=listY+35,i=0;for(var server:servers){if(server==s)c.fill(mx+8,row-2,width-22,row+21,0xFF16251A);c.drawText(textRenderer,server.isRunning()?"●":"○",mx+16,row+3,server.isRunning()?GREEN:MUTED,false);c.drawText(textRenderer,server.name(),mx+32,row+3,server==s?GREEN:TEXT,true);c.drawText(textRenderer,type(server),mx+150,row+3,MUTED,false);c.drawText(textRenderer,server.isRunning()?"Running":"Stopped",width-158,row+3,server.isRunning()?GREEN:MUTED,false);row+=24;if(++i>=2)break;}if(servers.isEmpty())c.drawText(textRenderer,"No servers found — create one above.",mx+14,row+3,MUTED,false);
        c.drawText(textRenderer,status,mx,height-13,MUTED,false);super.render(c,mouseX,mouseY,delta);
    }

    @Override public boolean mouseClicked(double x,double y,int b){if(b!=0)return super.mouseClicked(x,y,b);int mx=mainX(),rx=rightX();
        if(x>=14&&x<=162){if(y>=78&&y<120)nav=0;else if(y>=126&&y<168){nav=1;downloadJava();}else if(y>=174&&y<216)nav=2;else if(y>=222&&y<264)nav=3;else if(y>=270&&y<312)nav=4;return true;}
        if(x>=width-44&&y>=13&&y<=38){client.setScreen(parent);return true;}
        if(x>=mx+180&&x<=mx+252&&y>=40&&y<=65){createServer();return true;}
        if(x>=width-220&&x<=width-128&&y>=height-109&&y<=height-87){nameBox.setFocused(true);return true;}
        if(x>=width-120&&y>=height-109&&y<=height-87){refresh();return true;}
        if(y>=180&&y<=207){if(x>=mx&&x<mx+94)start();else if(x>=mx+101&&x<mx+195)stop();else if(x>=mx+202&&x<mx+296){stop();status="Restart requested";}else if(x>=mx+303&&x<mx+383){var t=target();if(t!=null&&t.process()!=null)t.process().destroyForcibly();status="Process killed";}return true;}
        int listY=height-116;if(y>=listY+31&&y<=height-14){int idx=(int)((y-(listY+33))/24);var l=MinehosterClient.SERVERS.all();if(idx>=0&&idx<l.size()){selected=l.get(idx);logOffset=0;return true;}}
        if(x>=rx+140&&x<=rx+206&&y>=364&&y<=391){installPlugin();return true;}
        int cy=218,ch=Math.max(150,height-322),cw=consoleW();if(y>=cy&&y<=cy+ch){if(x>=mx+cw-76&&y<=cy+30){status="Console remains available in the live log view.";return true;}commandBox.setFocused(true);}
        return super.mouseClicked(x,y,b);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(x>=mainX()&&x<=mainX()+consoleW()){logOffset=Math.max(0,logOffset-(int)v);return true;}return super.mouseScrolled(x,y,h,v);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256){client.setScreen(parent);return true;}if(key==257&&commandBox!=null&&commandBox.isFocused()){sendCommand();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void close(){client.setScreen(parent);}
}
