package dev.oreandorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

public final class OreAndOrderClient implements ClientModInitializer {
    private static String selection="";
    @Override public void onInitializeClient() {
        MenuScreens.register(TownhouseMenu.TYPE, TownhouseScreen::new);
        EntityRendererRegistry.register(WorkerTypes.WORKER, VillagerRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(VillagerPackets.Snapshot.TYPE,(packet,context) -> {
            var state=new Gson().fromJson(packet.json(),VillagerMenuState.class);
            if(context.client().gui.screen() instanceof VillagerActionScreen screen) screen.update(state);
            else if(packet.open()) context.client().gui.setScreen(new VillagerActionScreen(state));
        });
        ClientPlayNetworking.registerGlobalReceiver(VillagerPackets.Selection.TYPE,(packet,context) -> {
            selection=packet.hint();
            if(!selection.isEmpty() && context.client().gui.screen() instanceof VillagerActionScreen) context.client().gui.setScreen(null);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> selection="");
        UseBlockCallback.EVENT.register((player,level,hand,hit) -> level.isClientSide() && hand==InteractionHand.MAIN_HAND && !selection.isEmpty()?InteractionResult.SUCCESS:InteractionResult.PASS);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("oreandorder","selection"),(graphics,delta) -> {
            if(selection.isEmpty()) return;
            var client=Minecraft.getInstance(); int width=client.getWindow().getGuiScaledWidth();
            var lines=client.font.split(net.minecraft.network.chat.Component.literal(selection),Math.min(380,width-24));
            int y=12;
            for(var line:lines) {
                graphics.fill(8,y-3,width-8,y+12,0xDD101820);
                graphics.text(client.font,line,14,y,0xFFFFDA89);y+=14;
            }
        });
    }
}
