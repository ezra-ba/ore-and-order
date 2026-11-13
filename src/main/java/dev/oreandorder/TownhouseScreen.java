package dev.oreandorder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class TownhouseScreen extends AbstractContainerScreen<TownhouseMenu> {
    private Button recruit;

    TownhouseScreen(TownhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 183);
        inventoryLabelY = TownhouseMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        recruit = addRenderableWidget(Button.builder(Component.translatable("menu.oreandorder.townhouse.recruit"),
                button -> {
                    if (minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TownhouseMenu.RECRUIT_BUTTON);
                    }
                }).bounds(leftPos + 58, topPos + 35, 103, 20).build());
        recruit.active = menu.hasPayment();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        recruit.active = menu.hasPayment();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF36271D);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF58504A);
        graphics.fill(x, y, x + imageWidth - 2, y + imageHeight - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6);
        graphics.fill(x + 7, y + 22, x + imageWidth - 7, y + 80, 0xFFDFCEA9);
        for (var slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFF9F9F9);
            graphics.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF373737);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, Component.translatable("menu.oreandorder.townhouse.payment"),
                13, 64, 0xFF493927, false);
    }
}
