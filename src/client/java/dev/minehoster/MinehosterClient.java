package dev.minehoster;

import dev.minehoster.mixin.ScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MinehosterClient implements ClientModInitializer {
    public static final ServerManager SERVERS = new ServerManager();

    @Override
    public void onInitializeClient() {
        SERVERS.init();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof GameMenuScreen)) return;
            int x = screen.width / 2 - 100;
            int y = screen instanceof TitleScreen ? screen.height / 4 + 72 : screen.height - 48;
            ((ScreenAccessor) (Object) screen).minehoster$addDrawableChild(
                    ButtonWidget.builder(Text.literal("Minehoster"), b -> client.setScreen(new MinehosterScreen(screen)))
                            .dimensions(x, y, 200, 20).build());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> SERVERS.tick());
        Runtime.getRuntime().addShutdownHook(new Thread(SERVERS::shutdownAll, "Minehoster-Shutdown"));
    }

    public static MinecraftClient mc() { return MinecraftClient.getInstance(); }
}
