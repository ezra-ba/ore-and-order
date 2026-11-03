package dev.oreandorder;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public final class VillagerPackets {
    public enum Action { REFRESH, TRADE, RECRUIT, EQUIP, UNEQUIP, SELECT_STORAGE, SELECT_FIRST, SELECT_SECOND, MINE, ORDER, CANCEL, CLOSE }
    public record Request(UUID entity, Action action, String value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("oreandorder", "villager_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
            (buf,p) -> { buf.writeUUID(p.entity); buf.writeEnum(p.action); buf.writeUtf(p.value,500); },
            buf -> new Request(buf.readUUID(),buf.readEnum(Action.class),buf.readUtf(500)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(String json,boolean open) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("oreandorder", "villager_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
            (buf,p) -> { buf.writeUtf(p.json,16384); buf.writeBoolean(p.open); }, buf -> new Snapshot(buf.readUtf(16384),buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Selection(String hint) implements CustomPacketPayload {
        public static final Type<Selection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("oreandorder", "worker_selection"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Selection> CODEC = StreamCodec.of(
            (buf,p) -> buf.writeUtf(p.hint,160), buf -> new Selection(buf.readUtf(160)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Selection.TYPE,Selection.CODEC);
    }
    private VillagerPackets() {}
}
