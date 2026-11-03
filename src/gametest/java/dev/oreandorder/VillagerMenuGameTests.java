package dev.oreandorder;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;

public final class VillagerMenuGameTests {
    @GameTest(structure="oreandorder_test:floor",maxTicks=30)
    public void recruitmentPreservesVillagerAndChecksPermissions(GameTestHelper helper) {
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) helper.setBlock(x,0,z,Blocks.STONE);
        var villager=helper.spawn(EntityTypes.VILLAGER,new BlockPos(5,1,5));
        villager.setCustomName(Component.literal("Ada"));
        villager.setNoAi(true);
        villager.getInventory().addItem(new ItemStack(Items.BREAD,3));
        villager.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        var player=helper.makeMockServerPlayerInLevel(); player.setPos(villager.position().add(1,0,0));
        var server=helper.getLevel().getServer();
        // The game-test server's default operator level is 0; assign the production action level explicitly.
        server.getPlayerList().op(player.nameAndId(),java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),java.util.Optional.empty());
        try {
            var initial=VillagerMenus.snapshot(player,villager,"");
            helper.assertTrue(initial.canRecruit(),"An operator without a worker can recruit an adult villager");
            var worker=OreAndOrder.workers.recruit(player.getUUID(),villager);
            helper.assertTrue(villager.isRemoved(),"Recruitment must replace, not clone, the villager");
            helper.assertTrue(worker.getName().getString().equals("Ada"),"Custom name must be preserved");
            helper.assertTrue(!worker.isNoAi(),"Recruited workers must enable their movement AI");
            helper.assertTrue(worker.getMainHandItem().is(Items.STONE_PICKAXE),"Equipped tool must be preserved");
            helper.assertTrue(worker.getInventory().countItem(Items.BREAD)==3,"Food must transfer exactly once");
            var owned=VillagerMenus.snapshot(player,worker,"");
            helper.assertTrue(owned.owned() && owned.canManage(),"Owner with permission gets worker controls");
            VillagerMenus.requireManager(player,worker);
            var other=new ServerPlayer(server,helper.getLevel(),new GameProfile(UUID.randomUUID(),"Other"),ClientInformation.createDefault());
            other.setPos(worker.position().add(1,0,0));
            var hidden=VillagerMenus.snapshot(other,worker,"");
            helper.assertTrue(!hidden.owned() && !hidden.canManage() && hidden.inventory().isEmpty(),"Another player cannot inspect inventory or control the worker");
            server.getPlayerList().deop(player.nameAndId());
            boolean rejected=false;
            try { VillagerMenus.requireManager(player,worker); } catch(IllegalStateException expected) {rejected=true;}
            helper.assertTrue(rejected,"Ownership alone must not bypass operator permissions");
            player.setPos(worker.position().add(20,0,0));
            helper.assertTrue(!VillagerMenus.near(player,worker),"Distant actions must be rejected");
            boolean duplicate=false;
            try { OreAndOrder.workers.recruit(player.getUUID(),helper.spawn(EntityTypes.VILLAGER,new BlockPos(9,1,5))); }
            catch(IllegalStateException expected) {duplicate=true;}
            helper.assertTrue(duplicate,"Recruitment must respect the one-worker limit");
        } finally { server.getPlayerList().deop(player.nameAndId()); }
        helper.succeed();
    }
    @GameTest(structure="oreandorder_test:floor",maxTicks=20)
    public void menuPacketRoundTrip(GameTestHelper helper) {
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            var request=new VillagerPackets.Request(UUID.randomUUID(),VillagerPackets.Action.MINE,"minecraft:raw_iron|16");
            VillagerPackets.Request.CODEC.encode(buffer,request);
            helper.assertTrue(request.equals(VillagerPackets.Request.CODEC.decode(buffer)),"Action identity and arguments must round-trip");
            var snapshot=new VillagerPackets.Snapshot("{\"status\":\"Idle\"}",true);
            VillagerPackets.Snapshot.CODEC.encode(buffer,snapshot);
            helper.assertTrue(snapshot.equals(VillagerPackets.Snapshot.CODEC.decode(buffer)),"Open flag must survive packet encoding");
            boolean rejected=false;
            try { VillagerPackets.Request.CODEC.encode(buffer,new VillagerPackets.Request(UUID.randomUUID(),VillagerPackets.Action.ORDER,"x".repeat(501))); }
            catch(RuntimeException expected) {rejected=true;}
            helper.assertTrue(rejected,"Orders larger than the protocol limit must be rejected");
        } finally { buffer.release(); }
        helper.succeed();
    }
}
