package dev.oreandorder;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static dev.oreandorder.VillagerPackets.Action;

/** All action permissions and inventory changes are checked on the logical server. */
public final class VillagerMenus {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final class Session {
        UUID entity; long expires, nextAction, nextRefresh; Action selection; String notice = "";
        Session(UUID entity, long tick) { this.entity=entity; this.expires=tick+1200; }
    }
    public static void initialize() {
        VillagerPackets.register();
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit) -> {
            if (!(entity instanceof Villager villager) || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) open(serverPlayer,villager);
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Session session=SESSIONS.get(player.getUUID());
            if (session == null || session.selection == null) return InteractionResult.PASS;
            selectBlock(serverPlayer,hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(VillagerPackets.Request.TYPE,(packet,context) -> handle(context.player(),packet));
    }
    public static void clear() { SESSIONS.clear(); }
    public static void tick(MinecraftServer server) {
        long now=server.getTickCount();
        SESSIONS.entrySet().removeIf(entry -> {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            boolean expired=player==null || entry.getValue().expires<now;
            if (expired && player!=null && entry.getValue().selection!=null) ServerPlayNetworking.send(player,new VillagerPackets.Selection(""));
            return expired;
        });
    }
    public static void open(ServerPlayer player,Villager villager) {
        if (!near(player,villager)) return;
        SESSIONS.put(player.getUUID(),new Session(villager.getUUID(),player.level().getServer().getTickCount()));
        ServerPlayNetworking.send(player,new VillagerPackets.Selection(""));
        send(player,villager,"",true);
    }
    static boolean near(ServerPlayer player,Villager villager) {
        return within(player,villager,8);
    }
    private static boolean within(ServerPlayer player,Villager villager,int blocks) {
        return !player.isSpectator() && player.isAlive() && villager.isAlive() && villager.level()==player.level()
            && player.distanceToSqr(villager)<=blocks*blocks;
    }
    static boolean owns(UUID player,Villager villager) {
        return OreAndOrder.workers!=null && villager.getUUID().equals(OreAndOrder.workers.binding(player).worker);
    }
    static void requireManager(ServerPlayer player,Villager villager) {
        if (!within(player,villager,64)) throw problem("Worker is too far away. Return to it to open the menu.");
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) throw problem("Worker actions require operator level 2 or cheats enabled.");
        if (!(villager instanceof WorkerEntity) || !owns(player.getUUID(),villager)) throw problem("This worker belongs to another player.");
    }
    static VillagerMenuState snapshot(ServerPlayer player,Villager villager,String notice) {
        var workers=OreAndOrder.workers; UUID owner=player.getUUID(); boolean worker=villager instanceof WorkerEntity;
        boolean own=worker && owns(owner,villager);
        boolean permission=player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        boolean manage=own && permission;
        boolean busy=own && (workers.busy(owner) || OreAndOrder.instance.planning(owner));
        var binding=workers.binding(owner);
        List<VillagerMenuState.Stack> inventory=new ArrayList<>();
        if (own) for(int slot=0;slot<villager.getInventory().getContainerSize();slot++) {
            ItemStack stack=villager.getInventory().getItem(slot);
            inventory.add(new VillagerMenuState.Stack(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.isEmpty()?"Empty":stack.getHoverName().getString(),stack.getCount()));
        }
        String status=worker ? (own ? OreAndOrder.instance.activityForMenu(owner) : "This worker belongs to another player.")
            : (villager.isBaby()?"This villager is too young to work or trade.":"Trade here, or recruit this villager as your worker.");
        if (worker && own && !permission) status="Worker controls require operator level 2 or cheats enabled.";
        String storage=own && binding.chest!=null ? BlockPos.of(binding.chest).toShortString():"Not set";
        String area=own && binding.first!=null && binding.second!=null ? BlockPos.of(binding.first).toShortString()+" → "+BlockPos.of(binding.second).toShortString():"Not set";
        boolean ready=own && binding.chest!=null && binding.first!=null && binding.second!=null && !villager.getMainHandItem().isEmpty();
        return new VillagerMenuState(villager.getUUID(),villager.getName().getString(),worker,own,manage,
            !worker && permission && !villager.isBaby() && !villager.isTrading() && binding.worker==null,
            !worker && !villager.isBaby() && !villager.isTrading() && !villager.getOffers().isEmpty(),busy,ready,status,notice,storage,area,
            own && !villager.getMainHandItem().isEmpty()?villager.getMainHandItem().getHoverName().getString():"No pickaxe",inventory);
    }
    private static void send(ServerPlayer player,Villager villager,String notice) {
        send(player,villager,notice,false);
    }
    private static void send(ServerPlayer player,Villager villager,String notice,boolean open) {
        ServerPlayNetworking.send(player,new VillagerPackets.Snapshot(GSON.toJson(snapshot(player,villager,notice)),open));
    }
    private static Villager resolve(ServerPlayer player,UUID id) {
        var entity=player.level().getEntity(id);
        if (!(entity instanceof Villager villager) || !villager.isAlive()) throw problem("Villager is no longer available.");
        return villager;
    }
    private static void handle(ServerPlayer player,VillagerPackets.Request packet) {
        Session session=SESSIONS.get(player.getUUID()); long now=player.level().getServer().getTickCount();
        if (session==null || !session.entity.equals(packet.entity()) || session.expires<now) return;
        if (packet.action()==Action.CLOSE) { SESSIONS.remove(player.getUUID()); return; }
        // A forged client cannot use this receiver to spam provider calls or mutate another worker.
        if(packet.action()==Action.REFRESH) {
            if(now<session.nextRefresh) return;
            session.nextRefresh=now+10;
        } else {
            if(now<session.nextAction) return;
            session.nextAction=now+2;
        }
        session.expires=now+1200;
        Villager villager;
        try { villager=resolve(player,packet.entity()); }
        catch (IllegalStateException e) { SESSIONS.remove(player.getUUID()); return; }
        try {
            if (!(villager instanceof WorkerEntity && owns(player.getUUID(),villager) ? within(player,villager,64) : near(player,villager)))
                throw problem("Villager is too far away. Return to it to open the menu.");
            if (packet.action()==Action.REFRESH) { send(player,villager,session.notice); return; }
            var workers=OreAndOrder.workers; UUID owner=player.getUUID();
            if (packet.action()==Action.TRADE) {
                if (!snapshot(player,villager,"").canTrade()) throw problem("This villager has no trades available.");
                SESSIONS.remove(owner); villager.mobInteract(player,InteractionHand.MAIN_HAND); return;
            }
            if (packet.action()==Action.RECRUIT) {
                if (!snapshot(player,villager,"").canRecruit()) throw problem("Recruitment requires an adult villager, operator permissions, and no existing worker.");
                WorkerEntity recruited=workers.recruit(owner,villager);
                session.entity=recruited.getUUID(); session.notice="Recruited. Open Setup to equip a tool and choose storage.";
                send(player,recruited,session.notice); return;
            }
            requireManager(player,villager);
            switch(packet.action()) {
                case EQUIP -> { workers.equip(owner,player); session.notice="Pickaxe equipped from your main hand."; }
                case UNEQUIP -> { workers.unequip(owner,player); session.notice="Tool returned to you."; }
                case CANCEL -> { OreAndOrder.instance.cancelOrder(owner); session.notice="Order cancelled. Carried items are retained."; }
                case ORDER -> { session.notice=OreAndOrder.instance.orderFromMenu(player,packet.value())==1?"Order submitted. Check the live status above.":"Order could not start. See chat for details."; }
                case MINE -> {
                    String[] values=packet.value().split("\\|",-1);
                    if(values.length!=2) throw problem("Choose a resource and quantity.");
                    if(OreAndOrder.instance.planning(owner)) throw problem("Wait for the pending order, or cancel it first.");
                    workers.start(owner,new TaskPlan(values[0],Integer.parseInt(values[1]),"base_storage")); OreAndOrder.instance.clearPlanHistory(owner); session.notice="Mining order started.";
                }
                case SELECT_STORAGE, SELECT_FIRST, SELECT_SECOND -> {
                    if (workers.busy(owner) || OreAndOrder.instance.planning(owner)) throw problem("Cancel the current order before changing setup.");
                    session.selection=packet.action();
                    String label=packet.action()==Action.SELECT_STORAGE?"a chest or barrel":packet.action()==Action.SELECT_FIRST?"the first area corner":"the second area corner";
                    ServerPlayNetworking.send(player,new VillagerPackets.Selection("Right-click "+label+". Sneak + right-click to cancel.")); return;
                }
                default -> throw problem("Action unavailable.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) { session.notice=e.getMessage(); }
        send(player,villager,session.notice);
    }
    private static void selectBlock(ServerPlayer player,BlockPos pos) {
        Session session=SESSIONS.get(player.getUUID());
        if(session==null || session.selection==null) return;
        Villager villager;
        try {
            villager=resolve(player,session.entity);
            if(session.expires<player.level().getServer().getTickCount() || player.isSpectator()
                || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || !owns(player.getUUID(),villager)) throw problem("Selection expired or permission was lost.");
            if(!player.level().hasChunkAt(pos) || player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>49) throw problem("Choose a block within 6 blocks.");
            if(player.isShiftKeyDown()) session.notice="Selection cancelled.";
            else if(session.selection==Action.SELECT_STORAGE) { OreAndOrder.workers.storage(player.getUUID(),player.level(),pos); session.notice="Storage bound."; }
            else { OreAndOrder.workers.corner(player.getUUID(),player.level(),pos,session.selection==Action.SELECT_FIRST); session.notice="Area corner set."; }
            session.selection=null; ServerPlayNetworking.send(player,new VillagerPackets.Selection(""));
            // The player may now be far from the worker: finish setup in chat rather than showing remote controls.
            if(near(player,villager)) send(player,villager,session.notice,true);
            else { player.sendSystemMessage(Component.literal("[Ore & Order] "+session.notice+" Return to the worker to open its menu.")); SESSIONS.remove(player.getUUID()); }
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendSystemMessage(Component.literal("[Ore & Order] "+e.getMessage()));
        }
    }
    private static IllegalStateException problem(String message) { return new IllegalStateException(message); }
    private VillagerMenus() {}
}
