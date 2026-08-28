package dev.minehoster;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class MinehosterClient implements ClientModInitializer {
    public static final ServerManager SERVERS = new ServerManager();

    @Override
    public void onInitializeClient() {
        SERVERS.init();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
            int x = screen.width / 2 - 100;
            int y = screen instanceof TitleScreen ? screen.height / 4 + 72 : screen.height / 4 + 96;
            screen.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Minehoster"), b -> client.setScreen(new MinehosterScreen(screen)))
                    .bounds(x, y, 200, 20).build());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> SERVERS.tick());
        Runtime.getRuntime().addShutdownHook(new Thread(SERVERS::shutdownAll, "Minehoster-Shutdown"));
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
