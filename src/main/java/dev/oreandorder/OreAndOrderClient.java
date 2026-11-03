package dev.oreandorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.VillagerRenderer;

public final class OreAndOrderClient implements ClientModInitializer {
    @Override public void onInitializeClient() { EntityRendererRegistry.register(WorkerTypes.WORKER, VillagerRenderer::new); }
}
