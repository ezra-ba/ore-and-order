package dev.oreandorder;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class TownhouseBlock extends Block {
    private static final int MEAT_COST = 64;
    static final Identifier ID = Identifier.fromNamespaceAndPath("oreandorder", "townhouse_ledger");
    private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, ID);
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);
    static final Block BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
            new TownhouseBlock(Properties.ofFullCopy(Blocks.CRAFTING_TABLE).setId(BLOCK_KEY)));
    static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
            new BlockItem(BLOCK, new Item.Properties().useBlockDescriptionPrefix().setId(ITEM_KEY)));

    private TownhouseBlock(Properties properties) {
        super(properties);
    }

    static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(ITEM));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !stack.is(ItemTags.MEAT)) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (stack.is(Items.ROTTEN_FLESH)) {
                message(serverPlayer, "Rotten flesh cannot pay for a worker.");
            } else if (stack.getCount() < MEAT_COST) {
                message(serverPlayer, "Hold a full stack of 64 meat of one kind.");
            } else {
                recruit(serverLevel, pos, hit.getDirection(), serverPlayer, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            message(serverPlayer, "Right-click with 64 raw or cooked meat of one kind to recruit a worker.");
        }
        return InteractionResult.SUCCESS;
    }

    private static void recruit(ServerLevel level, BlockPos pos, Direction face, ServerPlayer player, ItemStack meat) {
        try {
            OreAndOrder.workers.spawnAtTownhouse(player.getUUID(), level, pos, face);
            meat.shrink(MEAT_COST);
            message(player, "Worker recruited. Right-click them to give them a job.");
        } catch (IllegalStateException failure) {
            message(player, failure.getMessage());
        }
    }

    private static void message(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[Ore & Order] " + message));
    }
}
