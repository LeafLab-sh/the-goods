package sh.leaflab.goods.client.gui;

import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

// A small floating mode-switch button to the left of the grid panel — same 18x18 size and "floats outside the
// panel" positioning as Refined Storage's AbstractSideButtonWidget, using its actual sprites (imported under
// REFINEDSTORAGE2_LICENSE.txt, MIT). Not every mode this mod needs has an RS equivalent icon (e.g. "sort by
// price" — RS has no currency concept), so the icon supplier may return null, falling back to a hand-drawn glyph.
public class SideButtonWidget extends AbstractWidget {
    public static final int SIZE = 18;
    private static final int ICON_SIZE = 16;

    private static final Identifier BASE = sprite("widget/side_button/base");
    private static final Identifier HOVERED = sprite("widget/side_button/hovered");
    private static final Identifier HOVER_OVERLAY = sprite("widget/side_button/hover_overlay");
    private static final int FALLBACK_TEXT_COLOR = 0xFF404040;

    private final Font font;
    private final Supplier<Identifier> iconSprite;
    private final Supplier<String> fallbackIconText;
    private final Supplier<Component> tooltipText;
    private final Runnable onPress;

    public SideButtonWidget(int x, int y, Font font, Supplier<Identifier> iconSprite,
                             Supplier<String> fallbackIconText, Supplier<Component> tooltipText, Runnable onPress) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.font = font;
        this.iconSprite = iconSprite;
        this.fallbackIconText = fallbackIconText;
        this.tooltipText = tooltipText;
        this.onPress = onPress;
        refreshTooltip();
    }

    public static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath("thegoods", path);
    }

    public void refreshTooltip() {
        this.setTooltip(Tooltip.create(tooltipText.get()));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(GUI_TEXTURED, this.isHoveredOrFocused() ? HOVERED : BASE, this.getX(), this.getY(), SIZE, SIZE);

        Identifier icon = iconSprite.get();
        if (icon != null) {
            graphics.blitSprite(GUI_TEXTURED, icon, this.getX() + 1, this.getY() + 1, ICON_SIZE, ICON_SIZE);
        } else {
            String text = fallbackIconText.get();
            int textWidth = font.width(text);
            graphics.text(font, text, this.getX() + (SIZE - textWidth) / 2, this.getY() + 5, FALLBACK_TEXT_COLOR, false);
        }

        if (this.isHoveredOrFocused()) {
            graphics.blitSprite(GUI_TEXTURED, HOVER_OVERLAY, this.getX(), this.getY(), SIZE, SIZE);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
        refreshTooltip();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, tooltipText.get());
    }
}
