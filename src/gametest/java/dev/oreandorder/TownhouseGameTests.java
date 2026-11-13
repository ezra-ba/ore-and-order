package dev.oreandorder;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
        var hit = new BlockHitResult(Vec3.atCenterOf(ledger), Direction.SOUTH, ledger, false);
        var state = level.getBlockState(ledger);
        var controller = OreAndOrder.workers;
        var binding = controller.binding(player.getUUID());
        ItemStack meat = new ItemStack(Items.COOKED_BEEF, 64);
        state.useItemOn(meat, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(meat.getCount() == 64 && binding.worker == null,
                "A blocked ledger must leave the meat and worker slot untouched");
        helper.setBlock(local.south(), Blocks.AIR);
        ItemStack shortStack = new ItemStack(Items.BEEF, 63);
        state.useItemOn(shortStack, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(shortStack.getCount() == 63 && binding.worker == null,
                "A partial stack must stay untouched");
        state.useItemOn(meat, level, player, InteractionHand.MAIN_HAND, hit);
        var worker = controller.worker(player.getUUID());
        helper.assertTrue(meat.isEmpty() && worker.blockPosition().equals(ledger.south()),
                "One stack should recruit one owned worker at the clear adjacent spot");
        VillagerMenus.requireManager(player, worker);
        helper.assertTrue(!VillagerMenus.snapshot(player, worker, "").canUseAi(),
                "Survival worker controls should not grant access to AI orders");
        ItemStack secondPayment = new ItemStack(Items.BEEF, 64);
        state.useItemOn(secondPayment, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(secondPayment.getCount() == 64 && controller.worker(player.getUUID()) == worker,
                "The existing worker should block duplicate recruitment without charging");
        helper.succeed();
    }
}
