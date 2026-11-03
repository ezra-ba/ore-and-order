package dev.oreandorder;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.*;

/** World-scoped bindings. Entity inventory/equipment use normal entity persistence. Jobs pause on reload. */
public final class WorkerSavedData extends SavedData {
    public static final class Binding {
        public UUID owner, worker;
        public String dimension;
        public Long chest, first, second;
        Binding(UUID owner) { this.owner = owner; }
    }
    private static final Gson GSON = new Gson();
    private static final Codec<WorkerSavedData> CODEC = Codec.STRING.xmap(json -> {
        WorkerSavedData data = new WorkerSavedData();
        for (Binding binding : GSON.fromJson(json, Binding[].class)) data.bindings.put(binding.owner, binding);
        return data;
    }, data -> GSON.toJson(data.bindings.values()));
    public static final SavedDataType<WorkerSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("oreandorder", "workers"), WorkerSavedData::new, CODEC, null);
    public final Map<UUID, Binding> bindings = new HashMap<>();
    public static WorkerSavedData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    public Binding binding(UUID owner) {
        return bindings.computeIfAbsent(owner, key -> { setDirty(); return new Binding(key); });
    }
}
