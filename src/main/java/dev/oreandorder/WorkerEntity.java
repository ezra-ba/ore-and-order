package dev.oreandorder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Vanilla villager physics and inventory, without village AI or trading. */
public final class WorkerEntity extends Villager {
    public WorkerEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }
    @Override protected void customServerAiStep(ServerLevel level) { /* Controller supplies navigation goals. */ }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean wantsToPickUp(ServerLevel level, ItemStack stack) { return false; }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int slot = 0; slot < getInventory().getContainerSize(); slot++) {
            ItemStack stack = getInventory().removeItemNoUpdate(slot);
            if (!stack.isEmpty()) spawnAtLocation(level, stack);
        }
    }
}
