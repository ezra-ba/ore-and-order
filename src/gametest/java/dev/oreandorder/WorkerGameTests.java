package dev.oreandorder;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.UUID;

public final class WorkerGameTests {
    private record Fixture(UUID owner, WorkerEntity worker, ChestBlockEntity chest, BlockPos ore) {}
    private Fixture fixture(GameTestHelper helper) {
        for (int x=0; x<16; x++) for(int z=0; z<16; z++) helper.setBlock(x,0,z,Blocks.STONE);
        helper.setBlock(2,1,9,Blocks.CHEST);
        helper.setBlock(8,1,3,Blocks.IRON_ORE);
        helper.setBlock(9,1,3,Blocks.DEEPSLATE_IRON_ORE);
        UUID owner = UUID.randomUUID(); var workers = OreAndOrder.workers;
        WorkerEntity worker = workers.spawn(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2,1,3)));
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        workers.storage(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2,1,9)));
        workers.corner(owner, helper.getLevel(), helper.absolutePos(new BlockPos(8,1,3)), true);
        workers.corner(owner, helper.getLevel(), helper.absolutePos(new BlockPos(9,1,3)), false);
        return new Fixture(owner, worker, helper.getBlockEntity(new BlockPos(2,1,9), ChestBlockEntity.class), helper.absolutePos(new BlockPos(8,1,3)));
    }
    private TaskPlan iron(int count) { return new TaskPlan("minecraft:raw_iron", count, "base_storage"); }
    private int count(net.minecraft.world.Container container, Item item) {
        int total=0; for(int slot=0;slot<container.getContainerSize();slot++) if(container.getItem(slot).is(item)) total+=container.getItem(slot).getCount(); return total;
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=1000)
    public void minesWalksAndDelivers(GameTestHelper helper) {
        Fixture f = fixture(helper); var workers = OreAndOrder.workers;
        workers.start(f.owner, iron(2));
        helper.succeedWhen(() -> {
            helper.assertTrue(!workers.busy(f.owner), "Worker must finish: " + workers.status(f.owner));
            helper.assertTrue(workers.status(f.owner).startsWith("Completed:"), workers.status(f.owner));
            helper.assertTrue(count(f.chest, Items.RAW_IRON)==2,"Both real ore drops must arrive in the chest");
            helper.assertTrue(f.worker.getInventory().isEmpty(),"Delivered items must leave worker inventory");
            helper.assertTrue(f.worker.getMainHandItem().getDamageValue()==2,"Mining must consume two durability points");
            helper.assertTrue(helper.getLevel().getBlockState(f.ore).isAir(),"Ore must actually be removed");
            helper.assertTrue(f.worker.getZ() > helper.absolutePos(new BlockPos(2,1,6)).getZ(),"Worker must walk back to storage");
        });
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=500)
    public void fullChestRetainsInventory(GameTestHelper helper) {
        Fixture f=fixture(helper); var workers=OreAndOrder.workers;
        for(int slot=0;slot<f.chest.getContainerSize();slot++) f.chest.setItem(slot,new ItemStack(Items.STONE,64));
        f.worker.getInventory().addItem(new ItemStack(Items.RAW_IRON));
        workers.start(f.owner,iron(1));
        helper.succeedWhen(() -> {
            helper.assertTrue(!workers.busy(f.owner),"Wait for storage attempt");
            helper.assertTrue(workers.status(f.owner).startsWith("Chest full"),workers.status(f.owner));
            helper.assertTrue(count(f.worker.getInventory(),Items.RAW_IRON)==1,"Full storage must retain carried item");
            helper.assertTrue(!helper.getLevel().getBlockState(f.ore).isAir(),"No extra ore should be mined");
        });
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=80)
    public void cancelPreservesOreAndInventory(GameTestHelper helper) {
        Fixture f=fixture(helper); var workers=OreAndOrder.workers;
        f.worker.getInventory().addItem(new ItemStack(Items.RAW_IRON));
        workers.start(f.owner,iron(2)); workers.cancel(f.owner);
        helper.runAfterDelay(40,() -> {
            helper.assertTrue(!workers.busy(f.owner),"Cancelled job must not restart");
            helper.assertTrue(count(f.worker.getInventory(),Items.RAW_IRON)==1,"Cancellation must retain inventory");
            helper.assertTrue(!helper.getLevel().getBlockState(f.ore).isAir(),"Cancelled worker must not mine"); helper.succeed();
        });
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=80)
    public void unsuitableToolAndAreaLimits(GameTestHelper helper) {
        Fixture f=fixture(helper); var workers=OreAndOrder.workers;
        f.worker.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        boolean rejected=false;
        try { workers.corner(f.owner,helper.getLevel(),f.ore.offset(16,0,0),false); } catch(IllegalStateException expected) { rejected=true; }
        helper.assertTrue(rejected,"Oversized area must be rejected");
        workers.start(f.owner,iron(1));
        helper.runAfterDelay(10,() -> {
            helper.assertTrue(!workers.busy(f.owner),"Wrong-tier pickaxe must stop job");
            helper.assertTrue(workers.status(f.owner).startsWith("Pickaxe cannot"), workers.status(f.owner));
            helper.assertTrue(!helper.getLevel().getBlockState(f.ore).isAir(),"Wrong tool must never destroy ore"); helper.succeed();
        });
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=100)
    public void enclosedOreRemainsUntouched(GameTestHelper helper) {
        Fixture f=fixture(helper); var workers=OreAndOrder.workers;
        for(int x=7;x<=10;x++) for(int z=2;z<=4;z++) for(int y=1;y<=3;y++) helper.setBlock(x,y,z,Blocks.STONE);
        helper.setBlock(8,1,3,Blocks.IRON_ORE);
        workers.start(f.owner,iron(1));
        helper.runAfterDelay(60,() -> {
            helper.assertTrue(!workers.busy(f.owner),"Inaccessible ore must stop the job");
            helper.assertTrue(!helper.getLevel().getBlockState(f.ore).isAir(),"Worker must not mine through walls"); helper.succeed();
        });
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=20)
    public void persistenceRoundTrip(GameTestHelper helper) {
        Fixture f=fixture(helper); var level=helper.getLevel();
        var parsed=level.getServer().getCommands().getDispatcher().parse("oreorder worker mine minecraft:raw_iron 2", level.getServer().createCommandSourceStack());
        helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"Registered mining command must accept namespaced item IDs");
        f.worker.getInventory().addItem(new ItemStack(Items.RAW_IRON,3));
        var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
        f.worker.saveWithoutId(output);
        var restored=WorkerTypes.WORKER.create(level,net.minecraft.world.entity.EntitySpawnReason.LOAD);
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
        helper.assertTrue(count(restored.getInventory(),Items.RAW_IRON)==3,"Worker inventory must survive entity serialization");
        helper.assertTrue(restored.getMainHandItem().is(Items.STONE_PICKAXE),"Tool must survive entity serialization");
        var data=WorkerSavedData.get(level.getServer());
        var encoded=WorkerSavedData.TYPE.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,data).getOrThrow();
        var decoded=WorkerSavedData.TYPE.codec().parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        var binding=decoded.binding(f.owner);
        helper.assertTrue(binding.worker.equals(f.worker.getUUID()) && binding.chest.equals(OreAndOrder.workers.binding(f.owner).chest),"Ownership and storage must survive saved-data serialization");
        helper.succeed();
    }

    @GameTest(structure="oreandorder_test:floor", maxTicks=20)
    public void insertsWithoutDuplication(GameTestHelper helper) {
        var storage=new SimpleContainer(1); storage.setItem(0,new ItemStack(Items.RAW_IRON,63));
        ItemStack carried=new ItemStack(Items.RAW_IRON,3); WorkerController.insert(storage,carried);
        helper.assertTrue(storage.getItem(0).getCount()==64 && carried.getCount()==2,"Transfer must conserve item count at stack capacity");
        WorkerController.insert(storage,carried);
        helper.assertTrue(carried.getCount()==2,"Repeated insertion into full storage must not lose items"); helper.succeed();
    }
}
