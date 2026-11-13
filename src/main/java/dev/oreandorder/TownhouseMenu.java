package dev.oreandorder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;

public final class TownhouseMenu extends AbstractContainerMenu {
    static final int RECRUIT_BUTTON = 0;
    static final int MEAT_COST = 64;
    static final int MEAT_X = 24;
    static final int MEAT_Y = 37;
    static final int INVENTORY_Y = 101;

    static final MenuType<TownhouseMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
            TownhouseBlock.ID, new MenuType<>(TownhouseMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private final ContainerLevelAccess access;
    private final Direction clickedFace;
    private final SimpleContainer payment = new SimpleContainer(1);

    private TownhouseMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL, Direction.UP);
    }

    TownhouseMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Direction clickedFace) {
        super(TYPE, containerId);
        this.access = access;
        this.clickedFace = clickedFace;
        addSlot(new Slot(payment, 0, MEAT_X, MEAT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsMeat(stack);
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    static void initialize() {}

    private static boolean acceptsMeat(ItemStack stack) {
        return stack.is(ItemTags.MEAT) && !stack.is(Items.ROTTEN_FLESH);
    }

    boolean hasPayment() {
        ItemStack stack = payment.getItem(0);
        return acceptsMeat(stack) && stack.getCount() >= MEAT_COST;
    }

    @Override
    public boolean stillValid(Player player) {
        return !player.isSpectator() && access.evaluate((level, pos) -> player.level() == level, true)
                && stillValid(access, player, TownhouseBlock.BLOCK);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != RECRUIT_BUTTON || !(player instanceof ServerPlayer serverPlayer)
                || !player.isAlive() || !stillValid(player) || !hasPayment()) return false;

        return access.evaluate((level, pos) -> {
            if (!(level instanceof ServerLevel serverLevel) || OreAndOrder.workers == null) return false;
            try {
                OreAndOrder.workers.spawnAtTownhouse(player.getUUID(), serverLevel, pos, clickedFace);
            } catch (IllegalStateException failure) {
                serverPlayer.sendSystemMessage(Component.literal("[Ore & Order] " + failure.getMessage()));
                return false;
            }
            payment.removeItem(0, MEAT_COST);
            serverPlayer.sendSystemMessage(Component.translatable("menu.oreandorder.townhouse.recruited"));
            return true;
        }, false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = stack.copy();
        boolean moved = index == 0
                ? moveItemStackTo(stack, 1, slots.size(), true)
                : acceptsMeat(stack) && moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) clearContainer(player, payment);
    }
}
