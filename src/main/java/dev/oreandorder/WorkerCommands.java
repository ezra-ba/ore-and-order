package dev.oreandorder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;

public final class WorkerCommands {
    @FunctionalInterface private interface Action { String run(CommandSourceStack source, ServerPlayer player) throws Exception; }
    private static int action(CommandSourceStack source, Action action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            String message = action.run(source, player);
            source.sendSuccess(() -> Component.literal("[Ore & Order] " + message), false); return 1;
        } catch (IllegalStateException | IllegalArgumentException e) {
            source.sendFailure(Component.literal("[Ore & Order] " + e.getMessage())); return 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw e; }
        catch (Exception e) { source.sendFailure(Component.literal("[Ore & Order] Worker command failed.")); return 0; }
    }
    public static LiteralArgumentBuilder<CommandSourceStack> node(java.util.function.Supplier<WorkerController> controller) {
        var root = Commands.literal("worker")
            .executes(ctx -> action(ctx.getSource(), (s,p) -> "spawn, storage, area pos1/pos2, equip, unequip, status, mine <item_id> <quantity>. Look at a block for storage and corners."))
            .then(Commands.literal("spawn").executes(ctx -> action(ctx.getSource(), (s,p) -> {
                var workers = controller.get();
                if (workers.binding(p.getUUID()).worker != null) throw new IllegalStateException("You already have a worker. Load its chunk to find it.");
                IllegalStateException last = null;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    try { workers.spawn(p.getUUID(), s.getLevel(), p.blockPosition().relative(direction, 2)); return "Worker spawned. Give it a pickaxe with /oreorder worker equip."; }
                    catch (IllegalStateException e) { last = e; }
                }
                throw last;
            })))
            .then(Commands.literal("storage").executes(ctx -> action(ctx.getSource(), (s,p) -> {
                controller.get().storage(p.getUUID(), s.getLevel(), lookedAt(p)); return "Storage bound to the chest/barrel you are looking at.";
            })))
            .then(Commands.literal("equip").executes(ctx -> action(ctx.getSource(), (s,p) -> {
                controller.get().equip(p.getUUID(), p); return "Pickaxe transferred to worker.";
            })))
            .then(Commands.literal("unequip").executes(ctx -> action(ctx.getSource(), (s,p) -> {
                controller.get().unequip(p.getUUID(), p); return "Tool returned.";
            })))
            .then(Commands.literal("status").executes(ctx -> action(ctx.getSource(), (s,p) -> controller.get().status(p.getUUID()))))
            .then(Commands.literal("mine").then(Commands.argument("item", IdentifierArgument.id())
                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 4096)).executes(ctx -> action(ctx.getSource(), (s,p) -> {
                    String item = IdentifierArgument.getId(ctx, "item").toString();
                    if(OreAndOrder.instance.planning(p.getUUID())) throw new IllegalStateException("Wait for the pending order, or cancel it first.");
                    String result=controller.get().start(p.getUUID(), new TaskPlan(item, IntegerArgumentType.getInteger(ctx, "quantity"), "base_storage"));
                    OreAndOrder.instance.clearPlanHistory(p.getUUID()); return result;
                })))));
        var area = Commands.literal("area");
        for (boolean first : new boolean[]{true, false}) {
            area.then(Commands.literal(first ? "pos1" : "pos2")
                .executes(ctx -> action(ctx.getSource(), (s,p) -> {
                    controller.get().corner(p.getUUID(), s.getLevel(), lookedAt(p), first); return "Area corner " + (first ? 1 : 2) + " set.";
                }))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> action(ctx.getSource(), (s,p) -> {
                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                    if (p.position().distanceToSqr(Vec3.atCenterOf(pos)) > 64*64) throw new IllegalStateException("Choose a nearby mining area.");
                    controller.get().corner(p.getUUID(), s.getLevel(), pos, first); return "Area corner " + (first ? 1 : 2) + " set to " + pos.toShortString() + ".";
                }))));
        }
        return root.then(area);
    }
    private static BlockPos lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(6, 1, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK)
            throw new IllegalStateException("Look at a block within 6 blocks.");
        return block.getBlockPos();
    }
}
