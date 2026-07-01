package sh.leaflab.goods.client.gui;

import java.util.function.Consumer;

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
// far more items than the vanilla Slot-array approach would scale to (see docs/trade-hub-menu-design.md).
public class CatalogCellWidget extends AbstractWidget {
    private final CatalogEntry entry;
    private final ItemStack displayStack;
    private final Consumer<CatalogEntry> onSelect;

    public CatalogCellWidget(int x, int y, CatalogEntry entry, Consumer<CatalogEntry> onSelect) {
        super(x, y, 18, 18, Component.empty());
        this.entry = entry;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
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
