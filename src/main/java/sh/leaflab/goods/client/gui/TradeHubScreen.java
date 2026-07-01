package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.menu.TradeHubMenu;

// Plain-color panel, not a textured background — a placeholder like the block texture from Milestone 1, easy to
// swap for real art later without touching layout/interaction logic. Colors approximate vanilla's stone-gray
// inventory look (light panel, sunken beveled slot wells, dark text) since there's no slot-cutout texture to blit.
public class TradeHubScreen extends AbstractContainerScreen<TradeHubMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int SLOT_WELL_COLOR = 0xFF8B8B8B;
    private static final int SLOT_BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BEVEL_DARK = 0xFF373737;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int BALANCE_COLOR = 0xFF7A5C00;

    public TradeHubScreen(TradeHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);
        for (Slot slot : this.menu.slots) {
            extractSlotWell(graphics, this.leftPos + slot.x, this.topPos + slot.y);
        }
    }

    // An 18x18 cell around a slot's 16x16 item area: dark edge at bottom/right, light edge at top/left, mid-gray
    // fill — the classic vanilla "sunken" slot bevel, drawn with three overlapping fills instead of a texture.
    private static void extractSlotWell(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BEVEL_DARK);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_BEVEL_LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_WELL_COLOR);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);

        String balanceText = Currency.format(this.menu.getClientBalance());
        int balanceWidth = this.font.width(balanceText);
        graphics.text(this.font, balanceText, this.imageWidth - balanceWidth - 8, this.titleLabelY, BALANCE_COLOR, false);

        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TITLE_COLOR, false);
    }
}
