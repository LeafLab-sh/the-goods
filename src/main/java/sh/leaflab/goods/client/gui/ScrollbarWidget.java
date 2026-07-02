package sh.leaflab.goods.client.gui;

import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

// A row-based scrollbar (drag the thumb, or use the mouse wheel), modeled directly on Refined Storage's
// ScrollbarWidget — same drag-offset math, wheel-tick behavior, and thumb sprite (imported under
// REFINEDSTORAGE2_LICENSE.txt, MIT), minus the smooth-scroll animation. Offset/maxOffset are in row units: the
// catalog holds its full result list client-side and scrolls through it locally (see CatalogWidget), unlike
// Milestone 6's original page-request-per-scroll design.
public class ScrollbarWidget extends AbstractWidget {
    private static final int WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    private static final Identifier TEXTURE = SideButtonWidget.sprite("widget/scrollbar");
    private static final Identifier TEXTURE_CLICKED = SideButtonWidget.sprite("widget/scrollbar_clicked");
    private static final Identifier TEXTURE_DISABLED = SideButtonWidget.sprite("widget/scrollbar_disabled");
    private static final int TRACK_COLOR = 0xFF8B8B8B;

    private double offset;
    private double maxOffset;
    private boolean dragging;
    private DoubleConsumer listener;

    public ScrollbarWidget(int x, int y, int height) {
        super(x, y, WIDTH, height, Component.empty());
    }

    public void setListener(DoubleConsumer listener) {
        this.listener = listener;
    }

    /** @param totalRows total rows currently available, @param visibleRows how many rows fit in the grid at once */
    public void configure(int totalRows, int visibleRows) {
        double newMaxOffset = Math.max(0, totalRows - visibleRows);
        this.maxOffset = newMaxOffset;
        this.active = newMaxOffset > 0;
        if (this.offset > newMaxOffset) {
            setOffset(newMaxOffset);
        }
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double newOffset) {
        double clamped = Math.max(0, Math.min(newOffset, maxOffset));
        if (clamped != this.offset) {
            this.offset = clamped;
            if (listener != null) {
                listener.accept(this.offset);
            }
        }
    }

    /** Lets the containing widget forward wheel scroll from anywhere over the grid, not just this thin strip. */
    public void scrollByWheel(double scrollY) {
        if (this.active) {
            int direction = (int) -Math.signum(scrollY);
            setOffset(offset + direction);
        }
    }

    private void updateOffsetFromMouse(double mouseY) {
        double track = this.height - SCROLLER_HEIGHT;
        if (track <= 0) {
            return;
        }
        setOffset(Math.floor((mouseY - SCROLLER_HEIGHT / 2.0 - this.getY()) / track * maxOffset));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || event.button() != 0) {
            return false;
        }
        if (event.x() >= this.getX() && event.x() <= this.getX() + this.width
                && event.y() >= this.getY() && event.y() <= this.getY() + this.height) {
            dragging = true;
            updateOffsetFromMouse(event.y());
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (dragging) {
            updateOffsetFromMouse(mouseY);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.active) {
            return false;
        }
        scrollByWheel(scrollY);
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, TRACK_COLOR);
        Identifier texture = !this.active ? TEXTURE_DISABLED : (dragging ? TEXTURE_CLICKED : TEXTURE);
        double track = this.height - SCROLLER_HEIGHT;
        int thumbY = this.getY() + (maxOffset <= 0 ? 0 : (int) (offset / maxOffset * track));
        graphics.blitSprite(GUI_TEXTURED, texture, this.getX(), thumbY, WIDTH, SCROLLER_HEIGHT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // intentionally empty
    }
}
