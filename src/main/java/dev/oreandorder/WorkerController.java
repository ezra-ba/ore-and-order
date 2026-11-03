package dev.oreandorder;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import java.util.*;

public final class WorkerController {
    private record Ore(Item item, Set<Block> blocks) {}
    private static Ore ore(Item item, Block... blocks) { return new Ore(item, Set.of(blocks)); }
    private static final Map<String, Ore> ORES = Map.of(
        "minecraft:raw_iron", ore(Items.RAW_IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
        "minecraft:raw_copper", ore(Items.RAW_COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
        "minecraft:raw_gold", ore(Items.RAW_GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
        "minecraft:coal", ore(Items.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        "minecraft:diamond", ore(Items.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
        "minecraft:emerald", ore(Items.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
        "minecraft:redstone", ore(Items.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
        "minecraft:lapis_lazuli", ore(Items.LAPIS_LAZULI, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE));
    private enum Phase { SELECT, WALK_TO_ORE, MINE, COLLECT, WALK_TO_CHEST }
    private static final class Work {
        final TaskPlan plan; final Ore ore;
        final Set<BlockPos> skipped = new HashSet<>();
        final List<UUID> drops = new ArrayList<>();
        Phase phase = Phase.SELECT;
        BlockPos target, stand;
        int delivered, ticks, airborneTicks;
        float health;
        Work(TaskPlan plan, Ore ore, float health) { this.plan = plan; this.ore = ore; this.health = health; }
    }
    private final MinecraftServer server;
    private final WorkerSavedData data;
    private final Map<UUID, Work> active = new HashMap<>();
    private final Map<UUID, String> statuses = new HashMap<>();
    private final Map<GlobalPos, UUID> reserved = new HashMap<>();

    public WorkerController(MinecraftServer server) { this.server = server; this.data = WorkerSavedData.get(server); }
    public WorkerSavedData.Binding binding(UUID owner) { return data.binding(owner); }
    private ServerLevel level(WorkerSavedData.Binding b) {
        if (b.dimension == null) throw problem("Spawn a worker first: /oreorder worker spawn");
        ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(b.dimension)));
        if (level == null) throw problem("Worker dimension is unavailable.");
        return level;
    }
    public WorkerEntity worker(UUID owner) {
        var b = binding(owner);
        if (b.worker == null) throw problem("Spawn a worker first: /oreorder worker spawn");
        var entity = level(b).getEntity(b.worker);
        if (!(entity instanceof WorkerEntity worker) || !worker.isAlive())
            throw problem("Worker is dead or its chunk is unloaded. Return to its last location.");
        return worker;
    }
    public WorkerEntity spawn(UUID owner, ServerLevel level, BlockPos pos) {
        var b = binding(owner);
        if (b.worker != null) throw problem("You already have a worker. Load its chunk to find it.");
        if (!safeStand(level, pos)) throw problem("Worker needs solid ground and two clear, dry blocks of space.");
        WorkerEntity worker = WorkerTypes.WORKER.create(level, EntitySpawnReason.COMMAND);
        if (worker == null) throw problem("Could not create worker.");
        worker.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        worker.setCustomName(Component.literal("Ore & Order Worker")); worker.setCustomNameVisible(true);
        worker.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        if (!level.addFreshEntity(worker)) throw problem("Could not spawn worker.");
        b.worker = worker.getUUID(); b.dimension = level.dimension().identifier().toString();
        b.chest = null; b.first = null; b.second = null;
        data.setDirty(); statuses.put(owner, "Idle. Bind storage, mark an area, and equip a pickaxe.");
        return worker;
    }
    public void died(WorkerEntity worker) {
        for (var b : data.bindings.values()) if (worker.getUUID().equals(b.worker)) {
            finish(b.owner, "Worker died. Its carried items were dropped."); b.worker = null; data.setDirty();
        }
    }
    private void editable(UUID owner) { if (active.containsKey(owner)) throw problem("Cancel the current job before changing worker setup."); }
    public void storage(UUID owner, ServerLevel level, BlockPos pos) {
        editable(owner); WorkerEntity worker = worker(owner); var b = binding(owner);
        if (worker.level() != level || worker.position().distanceTo(Vec3.atCenterOf(pos)) > 64) throw problem("Storage must be in the worker's dimension and within 64 blocks.");
        container(level, pos); b.chest = pos.asLong(); data.setDirty();
    }
    public void corner(UUID owner, ServerLevel level, BlockPos pos, boolean first) {
        editable(owner); WorkerEntity worker = worker(owner); var b = binding(owner);
        if (worker.level() != level || worker.position().distanceTo(Vec3.atCenterOf(pos)) > 48) throw problem("Area must be in the worker's dimension and within 48 blocks.");
        Long other = first ? b.second : b.first;
        if (other != null) validateArea(pos, BlockPos.of(other));
        if (first) b.first = pos.asLong(); else b.second = pos.asLong(); data.setDirty();
    }
    private static void validateArea(BlockPos a, BlockPos b) {
        if (Math.abs(a.getX()-b.getX()) >= 16 || Math.abs(a.getY()-b.getY()) >= 16 || Math.abs(a.getZ()-b.getZ()) >= 16)
            throw problem("Mining area may be at most 16 × 16 × 16 blocks.");
    }
    public void equip(UUID owner, ServerPlayer player) {
        editable(owner); WorkerEntity worker = worker(owner);
        if (worker.level() != player.level() || worker.distanceToSqr(player) > 64) throw problem("Stand within 8 blocks of your worker.");
        ItemStack held = player.getMainHandItem();
        if (!held.is(ItemTags.PICKAXES)) throw problem("Hold a pickaxe in your main hand.");
        if (!worker.getMainHandItem().isEmpty()) throw problem("Worker already has a tool. Use /oreorder worker unequip first.");
        worker.setItemSlot(EquipmentSlot.MAINHAND, held.split(1));
    }
    public void unequip(UUID owner, ServerPlayer player) {
        editable(owner); WorkerEntity worker = worker(owner);
        if (worker.level() != player.level() || worker.distanceToSqr(player) > 64) throw problem("Stand within 8 blocks of your worker.");
        ItemStack tool = worker.getMainHandItem(); worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!player.getInventory().add(tool) && !tool.isEmpty()) player.spawnAtLocation(level(binding(owner)), tool);
    }
    public String start(UUID owner, TaskPlan plan) {
        if (active.containsKey(owner)) throw problem("Worker is busy. Cancel the current job first.");
        if (!"base_storage".equals(plan.deliverTo())) throw problem("Unsupported delivery destination.");
        var b = binding(owner); WorkerEntity worker = worker(owner); ServerLevel level = level(b);
        Ore ore = ORES.get(plan.item());
        if (ore == null) throw problem("This worker only mines ore drops. For iron use 'Get " + plan.quantity() + " raw iron'. Smelting and crafting are not implemented.");
        if (plan.quantity() < 1 || plan.quantity() > 4096) throw problem("Quantity must be 1–4096.");
        if (b.chest == null || b.first == null || b.second == null) throw problem("Bind a chest and set both area corners first.");
        validateArea(BlockPos.of(b.first), BlockPos.of(b.second)); container(level, BlockPos.of(b.chest));
        if (worker.getMainHandItem().isEmpty()) throw problem("No pickaxe. Hold one and use /oreorder worker equip.");
        active.put(owner, new Work(plan, ore, worker.getHealth()));
        update(owner, "Working: acquire " + plan.quantity() + " × " + plan.item() + ".");
        return statuses.get(owner);
    }
    public boolean busy(UUID owner) { return active.containsKey(owner); }
    public String status(UUID owner) {
        String text = statuses.getOrDefault(owner, "Idle. Saved worker/setup retained; jobs do not resume after reload.");
        var b = binding(owner);
        return text + (b.worker == null ? " No worker assigned." : " Worker: " + b.worker)
                + " Storage: " + (b.chest == null ? "unset" : BlockPos.of(b.chest).toShortString())
                + " Area: " + (b.first == null ? "unset" : BlockPos.of(b.first).toShortString())
                + " → " + (b.second == null ? "unset" : BlockPos.of(b.second).toShortString());
    }
    public void cancel(UUID owner) { finish(owner, "Cancelled. Carried items remain in the worker; uncollected drops stay on the ground."); }
    public void stop() { for (UUID owner : List.copyOf(active.keySet())) finish(owner, "Paused by server shutdown. Submit a new order after loading."); }
    public void tick() {
        for (UUID owner : List.copyOf(active.keySet())) {
            try { step(owner, active.get(owner)); }
            catch (IllegalStateException e) { finish(owner, e.getMessage()); }
        }
    }
    private void step(UUID owner, Work work) {
        WorkerEntity worker = worker(owner); var b = binding(owner); ServerLevel level = level(b);
        if (worker.getHealth() < work.health || worker.isOnFire() || worker.isInWater()) throw problem("Stopped: worker is hurt or in danger.");
        work.health = worker.getHealth();
        // Newly spawned mobs need an entity tick to settle onto their floor before navigation can plan.
        if (!worker.onGround()) {
            if (++work.airborneTicks > 100) throw problem("Worker cannot find solid ground.");
            return;
        }
        work.airborneTicks = 0;
        if (++work.ticks > 600) throw problem("Cannot reach the target or collect its drops. Clear a safe route and try again.");
        switch (work.phase) {
            case SELECT -> select(owner, work, worker, level, b);
            case WALK_TO_ORE -> {
                if (!matches(level, work.target, work.ore)) { release(owner); work.phase = Phase.SELECT; work.ticks = 0; return; }
                if (arrived(worker, work.stand)) {
                    worker.getNavigation().stop();
                    if (!visible(worker, level, work.target)) { skip(owner, work); return; }
                    work.phase = Phase.MINE; work.ticks = 0;
                } else navigate(worker, work.stand, work.ticks);
            }
            case MINE -> mine(owner, work, worker, level);
            case COLLECT -> {
                if (work.ticks < 10) return;
                for (UUID id : work.drops) {
                    var entity = level.getEntity(id);
                    if (entity instanceof ItemEntity drop && drop.isAlive()) {
                        if (worker.distanceToSqr(drop) > 9) continue;
                        ItemStack remainder = worker.getInventory().addItem(drop.getItem().copy());
                        if (remainder.isEmpty()) drop.discard(); else drop.setItem(remainder);
                    }
                }
                boolean nearby = work.drops.stream().map(level::getEntity).anyMatch(e -> e instanceof ItemEntity && e.isAlive());
                if (!nearby || work.ticks > 60) {
                    work.drops.clear(); work.phase = Phase.SELECT; work.ticks = 0;
                }
            }
            case WALK_TO_CHEST -> {
                if (arrived(worker, work.stand)) { worker.getNavigation().stop(); deliver(owner, work, worker, level, b); }
                else navigate(worker, work.stand, work.ticks);
            }
        }
    }
    private void select(UUID owner, Work work, WorkerEntity worker, ServerLevel level, WorkerSavedData.Binding b) {
        if (count(worker.getInventory(), work.ore.item) >= work.plan.quantity() - work.delivered
                || !worker.getInventory().canAddItem(new ItemStack(work.ore.item))) {
            goChest(owner, work, worker, level, b); return;
        }
        if (!worker.getMainHandItem().is(ItemTags.PICKAXES)) throw problem("No pickaxe: the tool is missing or broke. Carried items are retained.");
        BlockPos min = BlockPos.min(BlockPos.of(b.first), BlockPos.of(b.second));
        BlockPos max = BlockPos.max(BlockPos.of(b.first), BlockPos.of(b.second));
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) throw problem("Mining area is not loaded. Stay nearby and submit a new order.");
            if (!work.skipped.contains(pos) && matches(level, pos, work.ore)
                    && !reserved.containsKey(GlobalPos.of(level.dimension(), pos))) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> worker.position().distanceToSqr(Vec3.atCenterOf(p))));
        if (candidates.isEmpty()) {
            if (count(worker.getInventory(), work.ore.item) > 0) { goChest(owner, work, worker, level, b); return; }
            finish(owner, "Stopped: no reachable, available ore remains. Delivered " + work.delivered + "/" + work.plan.quantity() + "."); return;
        }
        // Bound pathfinding per tick. Rejected candidates are remembered until a new order.
        for (BlockPos target : candidates.subList(0, Math.min(4, candidates.size()))) {
            BlockState state = level.getBlockState(target);
            if (!worker.getMainHandItem().isCorrectToolForDrops(state)) throw problem("Pickaxe cannot harvest this ore. Equip a suitable tool.");
            BlockPos stand = approach(worker, level, target, true);
            if (stand == null) { work.skipped.add(target); continue; }
            work.target = target; work.stand = stand; work.phase = Phase.WALK_TO_ORE; work.ticks = 0;
            reserved.put(GlobalPos.of(level.dimension(), target), owner);
            update(owner, "Walking to ore at " + target.toShortString() + ". Delivered " + work.delivered + "/" + work.plan.quantity() + "."); return;
        }
    }
    private void mine(UUID owner, Work work, WorkerEntity worker, ServerLevel level) {
        if (!matches(level, work.target, work.ore)) { skip(owner, work); return; }
        if (!arrived(worker, work.stand) || !visible(worker, level, work.target)) { skip(owner, work); return; }
        ItemStack tool = worker.getMainHandItem(); BlockState state = level.getBlockState(work.target);
        if (!tool.is(ItemTags.PICKAXES) || !tool.isCorrectToolForDrops(state)) throw problem("Pickaxe missing or cannot harvest this ore.");
        worker.getLookControl().setLookAt(Vec3.atCenterOf(work.target));
        worker.swingForAttack(InteractionHand.MAIN_HAND);
        int duration = Math.max(10, (int)Math.ceil(state.getDestroySpeed(level, work.target) * 30 / Math.max(1, tool.getDestroySpeed(state))));
        level.destroyBlockProgress(worker.getId(), work.target, Math.min(9, work.ticks * 10 / duration));
        if (work.ticks < duration) return;
        List<ItemStack> drops = Block.getDrops(state, level, work.target, null, worker, tool);
        if (drops.stream().noneMatch(stack -> stack.is(work.ore.item)))
            throw problem("This tool does not produce the requested ore drop. Use a pickaxe without Silk Touch.");
        if (!level.destroyBlock(work.target, false, worker)) throw problem("Block could not be mined.");
        for (ItemStack stack : drops) {
            ItemEntity drop = new ItemEntity(level, work.target.getX()+.5, work.target.getY()+.5, work.target.getZ()+.5, stack);
            drop.setNoPickUpDelay();
            if (level.addFreshEntity(drop)) work.drops.add(drop.getUUID());
        }
        tool.hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
        level.destroyBlockProgress(worker.getId(), work.target, -1); release(owner);
        work.phase = Phase.COLLECT; work.ticks = 0;
        update(owner, "Collecting ore drops. Delivered " + work.delivered + "/" + work.plan.quantity() + ".");
    }
    private void goChest(UUID owner, Work work, WorkerEntity worker, ServerLevel level, WorkerSavedData.Binding b) {
        BlockPos chest = BlockPos.of(b.chest); container(level, chest);
        BlockPos stand = approach(worker, level, chest, false);
        if (stand == null) throw problem("Cannot reach storage. Carried items are retained.");
        work.stand = stand; work.phase = Phase.WALK_TO_CHEST; work.ticks = 0;
        update(owner, "Returning to storage. Delivered " + work.delivered + "/" + work.plan.quantity() + ".");
    }
    private void deliver(UUID owner, Work work, WorkerEntity worker, ServerLevel level, WorkerSavedData.Binding b) {
        Container chest = container(level, BlockPos.of(b.chest));
        SimpleContainer inventory = worker.getInventory(); boolean blocked = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            int before = stack.getCount(); Item item = stack.getItem();
            insert(chest, stack); inventory.setChanged();
            if (item == work.ore.item) work.delivered += before - stack.getCount();
            if (!stack.isEmpty()) blocked = true;
        }
        if (work.delivered >= work.plan.quantity()) { finish(owner, "Completed: delivered " + work.delivered + " × " + work.plan.item() + " to storage."); return; }
        if (blocked) throw problem("Chest full. Carried items are retained; clear storage and submit a new order.");
        work.phase = Phase.SELECT; work.ticks = 0;
    }
    static void insert(Container chest, ItemStack stack) {
        for (int slot = 0; slot < chest.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!chest.canPlaceItem(slot, stack)) continue;
            ItemStack existing = chest.getItem(slot);
            int limit = Math.min(chest.getMaxStackSize(stack), stack.getMaxStackSize());
            if (existing.isEmpty()) {
                int amount = Math.min(limit, stack.getCount()); chest.setItem(slot, stack.split(amount));
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int amount = Math.min(Math.max(0, limit - existing.getCount()), stack.getCount());
                existing.grow(amount); stack.shrink(amount);
            }
        }
        chest.setChanged();
    }
    private static boolean matches(ServerLevel level, BlockPos pos, Ore ore) { return level.hasChunkAt(pos) && ore.blocks.contains(level.getBlockState(pos).getBlock()); }
    private static Container container(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) throw problem("Storage chunk is unloaded. Stay nearby.");
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof ChestBlockEntity chest) return chest;
        if (entity instanceof BarrelBlockEntity barrel) return barrel;
        throw problem("Storage is missing. Look at a chest or barrel and use /oreorder worker storage.");
    }
    private static BlockPos approach(WorkerEntity worker, ServerLevel level, BlockPos target, boolean lineOfSight) {
        List<BlockPos> stands = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = target.relative(direction).above(dy);
            if (safeStand(level, pos) && (!lineOfSight || visibleFrom(level, Vec3.atBottomCenterOf(pos).add(0, 1.6, 0), target, worker))) stands.add(pos);
        }
        stands.sort(Comparator.comparingDouble(p -> worker.position().distanceToSqr(Vec3.atBottomCenterOf(p))));
        for (BlockPos pos : stands) {
            if (arrived(worker, pos)) return pos;
            Path path = worker.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return pos;
        }
        return null;
    }
    private static boolean safeStand(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return false;
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        for (BlockPos check : List.of(pos, pos.above()))
            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty() || !level.getFluidState(check).isEmpty()) return false;
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-1,-1,-1), pos.offset(1,1,1))) {
            if (!level.hasChunkAt(check)) return false;
            Block block = level.getBlockState(check).getBlock();
            if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.MAGMA_BLOCK
                    || block == Blocks.CACTUS || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE || block == Blocks.POWDER_SNOW) return false;
        }
        return true;
    }
    private static boolean visible(WorkerEntity worker, ServerLevel level, BlockPos target) {
        return worker.position().distanceToSqr(Vec3.atCenterOf(target)) < 9 && visibleFrom(level, worker.getEyePosition(), target, worker);
    }
    private static boolean visibleFrom(ServerLevel level, Vec3 eye, BlockPos target, WorkerEntity worker) {
        BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, worker));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }
    private static boolean arrived(WorkerEntity worker, BlockPos stand) { return worker.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) < .65; }
    private static void navigate(WorkerEntity worker, BlockPos stand, int ticks) {
        if (ticks % 20 == 1 || worker.getNavigation().isDone()) {
            // The coordinate overload uses a one-block arrival tolerance; we need the actual standing cell.
            Path path = worker.getNavigation().createPath(stand, 0);
            if (path != null && path.canReach()) worker.getNavigation().moveTo(path, .65);
        }
    }
    private static int count(Container container, Item item) {
        int count = 0; for (int slot=0; slot<container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount(); return count;
    }
    private void skip(UUID owner, Work work) {
        try { if (work.target != null) level(binding(owner)).destroyBlockProgress(worker(owner).getId(), work.target, -1); }
        catch (IllegalStateException ignored) {}
        work.skipped.add(work.target); release(owner); work.phase = Phase.SELECT; work.ticks = 0;
    }
    private void release(UUID owner) { reserved.entrySet().removeIf(entry -> entry.getValue().equals(owner)); }
    private void update(UUID owner, String status) { statuses.put(owner, status); }
    private void finish(UUID owner, String status) {
        Work work = active.remove(owner); release(owner);
        try {
            WorkerEntity worker = worker(owner); worker.getNavigation().stop();
            if (work != null && work.target != null) level(binding(owner)).destroyBlockProgress(worker.getId(), work.target, -1);
        } catch (IllegalStateException ignored) {}
        statuses.put(owner, status);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) player.sendSystemMessage(Component.literal("[Ore & Order] " + status));
    }
    private static IllegalStateException problem(String message) { return new IllegalStateException(message); }
}
