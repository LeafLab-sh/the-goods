package sh.leaflab.goods.client.gui;

import java.util.function.Consumer;

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

import sh.leaflab.goods.network.CatalogEntry;

// One clickable grid cell in the catalog — a plain client-side widget, not a menu Slot, so the catalog can show
// far more items than the vanilla Slot-array approach would scale to (see docs/trade-hub-menu-design.md). The
// stock count overlay mirrors Refined Storage's per-tile amount display (ResourceSlotRendering#renderAmount).
public class CatalogCellWidget extends AbstractWidget {
    private static final String[] SUFFIXES = {"K", "M", "B", "T", "Q"};

    private final CatalogEntry entry;
    private final ItemStack displayStack;
    private final Font font;
    private final Consumer<CatalogEntry> onSelect;

    public CatalogCellWidget(int x, int y, CatalogEntry entry, Font font, Consumer<CatalogEntry> onSelect) {
        super(x, y, 18, 18, Component.empty());
        this.entry = entry;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
        this.font = font;
        this.onSelect = onSelect;
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
        renderAmount(graphics, this.getX() + 1, this.getY() + 1, formatStock(entry.stock()));
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

    // Floors to one decimal place, same rounding direction as this project's currency formatting elsewhere —
    // never overstates what's actually in stock.
    private static String formatStock(long stock) {
        if (stock < 1000) {
            return Long.toString(stock);
        }
        long divisor = 1000;
        int tier = 0;
        while (stock / divisor >= 1000 && tier < SUFFIXES.length - 1) {
            divisor *= 1000;
            tier++;
        }
        long whole = stock / divisor;
        long tenths = (stock % divisor) * 10 / divisor;
        return whole + "." + tenths + SUFFIXES[tier];
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onSelect.accept(entry);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, displayStack.getHoverName());
    }
}
