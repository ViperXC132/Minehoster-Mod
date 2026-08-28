package dev.minehoster;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public final class MinehosterScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameBox, pluginUrlBox, commandBox;
    private ServerManager.ManagedServer selected;
    private volatile String status = "Ready";
    private int logOffset;

    public MinehosterScreen(Screen parent){super(Text.literal("Minehoster"));this.parent=parent;}
    @Override protected void init(){
        super.init();
        nameBox=addDrawableChild(new TextFieldWidget(textRenderer,20,40,190,20,Text.literal("Server name")));nameBox.setPlaceholder(Text.literal("Server name"));
        pluginUrlBox=addDrawableChild(new TextFieldWidget(textRenderer,20,102,330,20,Text.literal("Plugin .jar URL")));pluginUrlBox.setPlaceholder(Text.literal("Plugin .jar URL"));
        commandBox=addDrawableChild(new TextFieldWidget(textRenderer,365,height-28,Math.max(80,width-465),20,Text.literal("Command")));commandBox.setPlaceholder(Text.literal("Command to send to server"));
        addDrawableChild(ButtonWidget.builder(Text.literal("Create"),b->createServer()).dimensions(215,40,75,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Java 21"),b->downloadJava()).dimensions(20,66,85,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Vanilla"),b->downloadVanilla()).dimensions(110,66,85,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Paper"),b->downloadPaper()).dimensions(200,66,85,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Install Plugin"),b->installPlugin()).dimensions(20,126,130,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"),b->start()).dimensions(255,160,80,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"),b->stop()).dimensions(255,184,80,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"),b->MinehosterClient.SERVERS.reload()).dimensions(155,126,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->client.setScreen(parent)).dimensions(width-100,10,80,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Send"),b->sendCommand()).dimensions(width-95,height-28,75,20).build());
    }
    private void createServer(){try{selected=MinehosterClient.SERVERS.create(nameBox.getText());status="Created "+selected.name();}catch(Exception e){status="Create failed: "+e.getMessage();}}
    private void downloadJava(){status="Downloading Java...";CompletableFuture.runAsync(()->{try{JreManager.downloadJava21(MinehosterClient.SERVERS.jres(),msg->status=msg);}catch(Exception e){status="Java download failed: "+e.getMessage();}});}
    private ServerManager.ManagedServer target(){if(selected!=null)return selected;var list=MinehosterClient.SERVERS.all();return list.isEmpty()?null:list.get(0);}
    private void downloadVanilla(){var s=target();if(s==null){status="Create a server first";return;}selected=s;status="Downloading Vanilla...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadVanilla(s.directory(),msg->status=msg);}catch(Exception e){status="Vanilla failed: "+e.getMessage();}});}
    private void downloadPaper(){var s=target();if(s==null){status="Create a server first";return;}selected=s;status="Downloading Paper...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPaper(s.directory(),msg->status=msg);}catch(Exception e){status="Paper failed: "+e.getMessage();}});}
    private void installPlugin(){var s=target();if(s==null){status="Create a server first";return;}String url=pluginUrlBox.getText().trim();if(url.isBlank()){status="Enter a plugin .jar URL";return;}selected=s;status="Installing plugin...";CompletableFuture.runAsync(()->{try{ServerDownloader.downloadPlugin(s.directory(),url,msg->status=msg);}catch(Exception e){status="Plugin failed: "+e.getMessage();}});}
    private void start(){var s=target();if(s==null){status="Create a server first";return;}selected=s;MinehosterClient.SERVERS.start(s.name());status="Starting "+s.name()+"...";}
    private void stop(){var s=target();if(s==null)return;MinehosterClient.SERVERS.stop(s.name());status="Stopping "+s.name()+"...";}
    private void sendCommand(){var s=target();if(s!=null){MinehosterClient.SERVERS.command(s.name(),commandBox.getText());commandBox.setText("");}}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        renderBackground(context,mouseX,mouseY,delta);context.drawText(textRenderer,"Minehoster — Server Manager",20,15,0xFFFFFF,true);context.drawText(textRenderer,status,20,height-48,0xAAAAAA,false);context.drawText(textRenderer,"Servers",365,18,0xFFFFFF,true);
        var servers=MinehosterClient.SERVERS.all();int y=42;for(var s:servers){boolean active=target()==s;int color=active?0x55FF55:0xFFFFFF;context.drawText(textRenderer,(s.isRunning()?"● ":"○ ")+s.name(),365,y,color,false);if(mouseX>=350&&mouseX<=width-20&&mouseY>=y-3&&mouseY<=y+12)context.fill(350,y-4,width-20,y+13,0x33000000);y+=16;}
        var s=target();if(s!=null){context.drawText(textRenderer,"Selected: "+s.name()+(s.isRunning()?" (RUNNING)":" (STOPPED)"),20,160,0xFFFFFF,true);context.drawText(textRenderer,"Server jar: "+(java.nio.file.Files.exists(s.serverJar())?"installed":"missing"),20,176,0xAAAAAA,false);context.drawText(textRenderer,"Live console",20,208,0xFFFFFF,true);var logs=s.logs();int maxLines=Math.max(1,(height-250)/10);int start=Math.max(0,logs.size()-maxLines-logOffset);int end=Math.min(logs.size(),start+maxLines);int ly=222;for(int i=start;i<end;i++){String line=logs.get(i);if(line.length()>95)line=line.substring(0,95);context.drawText(textRenderer,line,20,ly,0xDDDDDD,false);ly+=10;}}else context.drawText(textRenderer,"Create a server to get started.",20,160,0xAAAAAA,false);
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){if(button==0&&mouseX>=350&&mouseX<=width-20&&mouseY>=38&&mouseY<38+MinehosterClient.SERVERS.all().size()*16){int index=(int)((mouseY-38)/16);var list=MinehosterClient.SERVERS.all();if(index>=0&&index<list.size()){selected=list.get(index);logOffset=0;return true;}}return super.mouseClicked(mouseX,mouseY,button);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontalAmount,double verticalAmount){if(mouseX<350){logOffset=Math.max(0,logOffset-(int)verticalAmount);return true;}return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==256){client.setScreen(parent);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    @Override public void close(){client.setScreen(parent);}
}
