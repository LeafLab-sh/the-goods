package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import sh.leaflab.goods.menu.DepositorMenu;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

public class DepositorScreen extends AbstractContainerScreen<DepositorMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int SLOT_WELL_COLOR = 0xFF8B8B8B;
    private static final int SLOT_BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BEVEL_DARK = 0xFF373737;
    private static final int OWNER_LABEL_COLOR = 0xFF707070;
    private static final int OWNER_LABEL_Y = 16;
    private static final Identifier ROW_BACKGROUND = SideButtonWidget.sprite("grid/row");
    private static final int INVENTORY_ROW_WIDTH = 9 * 18;
    // Hotbar sits 58px below the main inventory's own Y (see AbstractContainerMenu#addStandardInventorySlots),
    // then a standard 6px margin below the hotbar row itself closes out the panel — same convention vanilla's
    // own Hopper screen uses for this exact 5-slot-row-plus-inventory layout.
    private static final int IMAGE_HEIGHT = DepositorMenu.PLAYER_INVENTORY_Y + 58 + 18 + 6;

    public DepositorScreen(DepositorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, IMAGE_HEIGHT);
        this.inventoryLabelY = DepositorMenu.PLAYER_INVENTORY_Y - 10;
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
            int y = this.topPos + DepositorMenu.DEPOSITOR_SLOTS_Y;
            extractSlotWell(graphics, x, y);
        }

        for (int row = 0; row < 3; row++) {
            int rowY = this.topPos + DepositorMenu.PLAYER_INVENTORY_Y + row * 18;
            graphics.blitSprite(GUI_TEXTURED, ROW_BACKGROUND, this.leftPos + 8, rowY, INVENTORY_ROW_WIDTH, 18);
        }
        graphics.blitSprite(GUI_TEXTURED, ROW_BACKGROUND, this.leftPos + 8, this.topPos + DepositorMenu.PLAYER_INVENTORY_Y + 58, INVENTORY_ROW_WIDTH, 18);
    }

    private static void extractSlotWell(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BEVEL_DARK);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_BEVEL_LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_WELL_COLOR);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);

        String ownerName = this.menu.getOwnerName();
        Component ownerLabel = Component.translatable("gui.thegoods.depositor.owner",
                ownerName != null && !ownerName.isEmpty() ? ownerName : Component.translatable("gui.thegoods.depositor.owner.unowned"));
        graphics.text(this.font, ownerLabel, this.titleLabelX, OWNER_LABEL_Y, OWNER_LABEL_COLOR, false);

        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
    }
}
