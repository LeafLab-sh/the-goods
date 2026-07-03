package sh.leaflab.goods.client.gui;

import java.util.function.BiConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.network.CatalogEntry;

// One clickable grid cell in the catalog — a plain client-side widget, not a menu Slot, so the catalog can show
// far more items than the vanilla Slot-array approach would scale to (see docs/trade-hub-menu-design.md). The
// stock count overlay mirrors Refined Storage's per-tile amount display (ResourceSlotRendering#renderAmount).
public class CatalogCellWidget extends AbstractWidget {
    private CatalogEntry entry;
    private ItemStack displayStack;
    private final Font font;
    // Boolean is "was Shift held" — a shift-click on the store's catalog grid (see docs/spec.md's own "store
    // inventory" framing) adds a full stack to the Buy quantity instead of just selecting the item.
    private final BiConsumer<CatalogEntry, Boolean> onSelect;

    public CatalogCellWidget(int x, int y, CatalogEntry entry, Font font, BiConsumer<CatalogEntry, Boolean> onSelect) {
        super(x, y, 18, 18, Component.empty());
        this.font = font;
        this.onSelect = onSelect;
        update(x, y, entry);
    }

    /** Repositions this cell and rebinds it to a different catalog entry, so CatalogWidget can reuse cell widgets
     * across scroll ticks and query results instead of reallocating the whole visible grid every time. */
    public void update(int x, int y, CatalogEntry entry) {
        this.setX(x);
        this.setY(y);
        this.entry = entry;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
        this.setTooltip(Tooltip.create(displayStack.getHoverName()));
    }

    public CatalogEntry entry() {
        return entry;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.isHoveredOrFocused()) {
            graphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + 17, this.getY() + 17, 0x80FFFFFF);
        }
        graphics.item(displayStack, this.getX() + 1, this.getY() + 1, 0);
        renderAmount(graphics, this.getX() + 1, this.getY() + 1, Currency.formatAbbreviated(entry.stock()));
    }

    // Refined Storage's ResourceSlotRendering#renderAmount only shrinks text wider than 16px, leaving short
    // amounts at normal (unscaled) font size — but a 16x16 slot reads as visually cramped at that size for any
    // amount, not just long ones, so this always takes RS's "doesn't fit" scaling branch instead.
    private void renderAmount(GuiGraphicsExtractor graphics, int slotX, int slotY, String text) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(slotX, slotY);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.text(font, text, 30 - font.width(text), 22, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onSelect.accept(entry, event.hasShiftDown());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, displayStack.getHoverName());
    }
}
