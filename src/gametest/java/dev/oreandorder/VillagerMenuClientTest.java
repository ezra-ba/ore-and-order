package dev.oreandorder;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.*;

public final class VillagerMenuClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
        var slots=new ArrayList<VillagerMenuState.Stack>();
        slots.add(new VillagerMenuState.Stack("minecraft:raw_iron","Raw Iron",12));
        slots.add(new VillagerMenuState.Stack("minecraft:coal","Coal",8));
        while(slots.size()<8) slots.add(new VillagerMenuState.Stack("minecraft:air","Empty",0));
        var worker=new VillagerMenuState(UUID.randomUUID(),"Ada · Mining worker",true,true,true,false,false,false,true,
            "Idle. Ready for a mining order.","","124, 64, -32","128, 64, -32 → 136, 66, -28","Stone Pickaxe",slots,true);
        context.setScreen(() -> new VillagerActionScreen(worker));
        context.waitTicks(5); context.takeScreenshot("worker-actions");
        context.clickScreenButton("Setup"); context.waitTicks(2); context.takeScreenshot("worker-setup");
        context.clickScreenButton("Inventory"); context.waitTicks(2); context.takeScreenshot("worker-inventory");
        context.runOnClient(client -> {
            var screen=client.gui.screen();
            for(var child:screen.children()) if(child instanceof AbstractWidget widget) {
                if(widget.getX()<0 || widget.getY()<0 || widget.getRight()>screen.width || widget.getBottom()>screen.height)
                    throw new AssertionError("Widget outside screen: "+widget.getMessage().getString());
            }
        });
        var villagerPreview=new VillagerMenuState(UUID.randomUUID(),"Villager",false,false,false,true,true,false,false,
            "Trade here, or recruit this villager as your worker.","","Not set","Not set","No pickaxe",List.of(),false);
        context.setScreen(() -> new VillagerActionScreen(villagerPreview));
        context.waitTicks(3); context.takeScreenshot("villager-actions");
        context.setScreen(() -> null);
            world.getServer().runCommand("fill -8 63 -8 8 63 8 stone");
            world.getServer().runCommand("fill -8 64 -8 8 69 8 air");
            world.getServer().runCommand("tp @a 0.5 64 1.5");
            world.getServer().runCommand("gamemode creative @a");
            var original=new java.util.concurrent.atomic.AtomicReference<UUID>();
            world.getServer().runOnServer(server -> {
                var player=server.getPlayerList().getPlayers().getFirst();
                server.getPlayerList().op(player.nameAndId(),Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),Optional.empty());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
                player.inventoryMenu.broadcastChanges();
                var villager=net.minecraft.world.entity.EntityTypes.VILLAGER.create(server.overworld(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                villager.setPos(.5,64,-.5);villager.setNoAi(true);villager.setCustomName(net.minecraft.network.chat.Component.literal("Ada"));
                server.overworld().addFreshEntity(villager);original.set(villager.getUUID());
            });
            context.waitTicks(15);
            context.getInput().lookAt(new net.minecraft.core.BlockPos(0,65,-1));
            context.waitFor(client -> client.crosshairPickEntity!=null && client.crosshairPickEntity.getUUID().equals(original.get()));
            context.takeScreenshot("villager-before-right-click");
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(VillagerActionScreen.class);
            context.takeScreenshot("villager-right-click-live");
            context.clickScreenButton("Recruit as worker");
            world.getServer().waitFor(server -> OreAndOrder.workers.binding(server.getPlayerList().getPlayers().getFirst().getUUID()).worker!=null);
            context.waitFor(client -> client.gui.screen() instanceof VillagerActionScreen screen && !screen.entity().equals(original.get()));
            context.clickScreenButton("Setup");
            context.clickScreenButton("Equip held pickaxe");
            world.getServer().waitFor(server -> OreAndOrder.workers.worker(server.getPlayerList().getPlayers().getFirst().getUUID()).getMainHandItem().is(net.minecraft.world.item.Items.STONE_PICKAXE));
            context.waitTicks(3);context.takeScreenshot("worker-menu-live");
        }
    }
}
