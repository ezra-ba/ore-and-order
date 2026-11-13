package dev.oreandorder;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class TownhouseGameTests {
    @GameTest(structure = "oreandorder_test:floor", maxTicks = 30)
    public void craftsLedgerAndChargesOnlyForSuccessfulSpawn(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (ShapedRecipe) level.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, TownhouseBlock.ID)).orElseThrow().value();
        ItemStack plank = new ItemStack(Items.SPRUCE_PLANKS);
        ItemStack stone = new ItemStack(Items.COBBLESTONE);
        CraftingInput input = CraftingInput.of(3, 3, List.of(plank, stone, plank,
                stone, new ItemStack(Items.BOOK), stone, plank, stone, plank));
        helper.assertTrue(recipe.matches(input, level) && recipe.assemble(input).is(TownhouseBlock.ITEM),
                "The recipe should craft a ledger from any planks, cobblestone and a book");

        BlockPos local = new BlockPos(6, 1, 6);
        BlockPos ledger = helper.absolutePos(local);
        helper.setBlock(local, TownhouseBlock.BLOCK);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(local.relative(direction).below(), Blocks.STONE);
            helper.setBlock(local.relative(direction), Blocks.STONE);
        }
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atBottomCenterOf(ledger.south(2)));
        var menu = new TownhouseMenu(1, player.getInventory(), ContainerLevelAccess.create(level, ledger), Direction.SOUTH);
        var payment = menu.getSlot(0);
        var controller = OreAndOrder.workers;
        var binding = controller.binding(player.getUUID());
        helper.assertTrue(!payment.mayPlace(new ItemStack(Items.ROTTEN_FLESH))
                && !payment.mayPlace(new ItemStack(Items.COBBLESTONE)), "Only suitable meat may enter the slot");
        player.getInventory().setItem(9, new ItemStack(Items.COOKED_BEEF, 64));
        menu.quickMoveStack(player, 1);
        helper.assertTrue(payment.getItem().getCount() == 64 && player.getInventory().getItem(9).isEmpty(),
                "Shift-click should move the meat into the payment slot");
        menu.clickMenuButton(player, TownhouseMenu.RECRUIT_BUTTON);
        helper.assertTrue(payment.getItem().getCount() == 64 && binding.worker == null,
                "A blocked ledger must leave the meat and worker slot untouched");
        helper.setBlock(local.south(), Blocks.AIR);
        payment.set(new ItemStack(Items.BEEF, 63));
        menu.clickMenuButton(player, TownhouseMenu.RECRUIT_BUTTON);
        helper.assertTrue(payment.getItem().getCount() == 63 && binding.worker == null,
                "A partial stack must stay untouched");
        payment.set(new ItemStack(Items.COOKED_BEEF, 64));
        menu.clickMenuButton(player, TownhouseMenu.RECRUIT_BUTTON);
        var worker = controller.worker(player.getUUID());
        helper.assertTrue(payment.getItem().isEmpty() && worker.blockPosition().equals(ledger.south()),
                "One stack should recruit one owned worker at the clear adjacent spot");
        VillagerMenus.requireManager(player, worker);
        helper.assertTrue(!VillagerMenus.snapshot(player, worker, "").canUseAi(),
                "Survival worker controls should not grant access to AI orders");
        payment.set(new ItemStack(Items.BEEF, 64));
        menu.clickMenuButton(player, TownhouseMenu.RECRUIT_BUTTON);
        helper.assertTrue(payment.getItem().getCount() == 64 && controller.worker(player.getUUID()) == worker,
                "The existing worker should block duplicate recruitment without charging");
        menu.removed(player);
        helper.assertTrue(payment.getItem().isEmpty() && player.getInventory().countItem(Items.BEEF) == 64,
                "Closing the ledger should return unused payment to the player");
        helper.succeed();
    }
}
