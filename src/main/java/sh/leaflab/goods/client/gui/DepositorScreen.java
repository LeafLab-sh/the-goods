package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import sh.leaflab.goods.menu.DepositorMenu;

public class DepositorScreen extends AbstractContainerScreen<DepositorMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int SLOT_WELL_COLOR = 0xFF8B8B8B;
    private static final int SLOT_BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BEVEL_DARK = 0xFF373737;
    private static final int CONTAINER_HEIGHT = 84;

    public DepositorScreen(DepositorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, CONTAINER_HEIGHT + 3 * 18 + 4 + 18 + 58);
        this.inventoryLabelY = CONTAINER_HEIGHT - 10;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);

        for (int i = 0; i < 5; i++) {
            int x = this.leftPos + 44 + i * 18;
            int y = this.topPos + 20;
            extractSlotWell(graphics, x, y);
        }
    }

    private static void extractSlotWell(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BEVEL_DARK);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_BEVEL_LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_WELL_COLOR);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
    }
}
