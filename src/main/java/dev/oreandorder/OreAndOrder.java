package dev.oreandorder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.CompletionException;

public final class OreAndOrder implements ModInitializer {
    private PlannerClient planner;
    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<UUID, Long> lastOrders = new HashMap<>();
    private record Job(UUID id, String status) {}

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { planner = new PlannerClient(PlannerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("ore-and-order.properties"))); }
            catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("oreandorder").error("Planner configuration could not be loaded. Check ore-and-order.properties; values are not logged.");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (planner != null) planner.close();
            planner = null; jobs.clear(); lastOrders.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            Commands.literal("oreorder")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> say(ctx.getSource(), "Ore & Order prototype: /oreorder ask <order>, status, cancel. Plans only; NPC execution is not implemented."))
                .then(Commands.literal("ask").then(Commands.argument("order", StringArgumentType.greedyString())
                    .executes(ctx -> ask(ctx.getSource(), StringArgumentType.getString(ctx, "order")))))
                .then(Commands.literal("status").executes(ctx -> {
                    UUID player = ctx.getSource().getPlayerOrException().getUUID();
                    Job job = jobs.get(player);
                    return say(ctx.getSource(), job == null ? "No task planned." : job.status());
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    UUID player = ctx.getSource().getPlayerOrException().getUUID();
                    jobs.put(player, new Job(UUID.randomUUID(), "Cancelled. No task will execute."));
                    return say(ctx.getSource(), "Cancelled. Any late model response will be ignored.");
                }))
        ));
    }

    private int ask(CommandSourceStack source, String order) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID player = source.getPlayerOrException().getUUID();
        if (planner == null) return fail(source, "Planner unavailable. Check the server configuration.");
        if (order.isBlank() || order.length() > 500) return fail(source, "Orders must contain 1–500 characters.");
        long now = System.nanoTime();
        if (lastOrders.containsKey(player) && now - lastOrders.get(player) < 10_000_000_000L)
            return fail(source, "Please wait 10 seconds between orders.");
        Job previous = jobs.get(player);
        if (previous != null && previous.status().equals("Planning…")) return fail(source, "An order is already being planned. Use /oreorder cancel first.");
        UUID id = UUID.randomUUID();
        jobs.put(player, new Job(id, "Planning…")); lastOrders.put(player, now);
        say(source, "Planning your order…");
        var server = source.getServer();
        planner.plan(order).whenComplete((json, error) -> {
            if (server.isStopped()) return;
            server.execute(() -> {
                Job current = jobs.get(player);
                if (current == null || !current.id().equals(id)) return;
                String result;
                boolean failed = error != null;
                if (failed) {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    result = cause instanceof PlannerClient.PlannerException ? cause.getMessage() : "Planner failed. Try again.";
                } else {
                    try {
                        TaskPlan plan = TaskPlan.parse(json, item -> {
                            Identifier identifier = Identifier.tryParse(item);
                            return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)
                                    && BuiltInRegistries.ITEM.getValue(identifier) != Items.AIR;
                        });
                        result = "Plan ready: acquire " + plan.quantity() + " × " + plan.item()
                                + " → " + plan.deliverTo() + ". Preview only; NPC execution is not implemented.";
                    } catch (IllegalArgumentException e) { failed = true; result = e.getMessage(); }
                }
                jobs.put(player, new Job(id, (failed ? "Failed: " : "") + result));
                var online = server.getPlayerList().getPlayer(player);
                if (online != null) online.sendSystemMessage(Component.literal("[Ore & Order] " + result));
            });
        });
        return 1;
    }

    private static int say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[Ore & Order] " + message), false); return 1;
    }
    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[Ore & Order] " + message)); return 0;
    }
}
