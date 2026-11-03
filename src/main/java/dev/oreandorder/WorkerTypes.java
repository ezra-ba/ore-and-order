package dev.oreandorder;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;

public final class WorkerTypes {
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("oreandorder", "worker"));
    public static final EntityType<WorkerEntity> WORKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.of(WorkerEntity::new, MobCategory.CREATURE).sized(0.6F, 1.95F).build(KEY));
    public static void initialize() { FabricDefaultAttributeRegistry.register(WORKER, Villager.createAttributes()); }
    private WorkerTypes() {}
}
