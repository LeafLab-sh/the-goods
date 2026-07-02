package sh.leaflab.goods.client.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.network.BuyRequestPayload;
import sh.leaflab.goods.network.CatalogEntry;

// Embedded panel next to the catalog, always visible (buttons just disabled with nothing selected) rather than a
// separate popup Screen or a panel that appears/disappears — +/-/max quantity and a live receipt computed with
// the exact same Currency methods the server charges with, so the preview can never disagree with the actual
// charge. Confirm sends the buy request with the quote hash echoed back; the server re-derives price from live
// stock regardless of anything computed here.
public class BuyDialog {
    // Minimum height reserved for the top row (icon is 18px tall even for a single-line name/placeholder). If the
    // name or placeholder wraps to more lines than that, everything below shifts down to match — see
    // recomputeLayout. These are deltas from wherever the controls row actually ends up, not from y.
    private static final int MIN_TOP_HEIGHT = 18;
    private static final int CONTROLS_TO_QTY_TEXT = 18;
    private static final int CONTROLS_TO_COST_TEXT = 28;
    private static final int CONTROLS_TO_CONFIRM = 38;
    private static final int TEXT_TOP_PADDING = 5;
    private static final int NO_SELECTION_WRAP_WIDTH = 80;
    private static final int ITEM_NAME_WRAP_WIDTH = 64;
    private static final int ITEM_NAME_X_OFFSET = 20;

    private final int x;
    private final int y;
    private final Font font;

    private CatalogEntry entry;
    private ItemStack displayStack = ItemStack.EMPTY;
    private long quantity;
    private long affordableMax;
    private int feePercent;
    private Button minusButton;
    private Button plusButton;
    private Button maxButton;
    private Button confirmButton;

    private List<FormattedCharSequence> topLines = List.of();
    private int controlsY;

    public BuyDialog(int x, int y, Font font, Consumer<AbstractWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.font = font;

        // Sized for the right-hand column (84px wide, right of the middle divider): -/+/Max share a row, Confirm
        // gets its own row below that (and below the receipt text — see extractExtra). Y positions get corrected
        // by recomputeLayout() below once there's real content to measure.
        minusButton = createStepButton(x, y, 18, 14, "-", false);
        plusButton = createStepButton(x + 20, y, 18, 14, "+", true);
        maxButton = Button.builder(Component.translatable("gui.thegoods.buy.max"), b -> setQuantity(affordableMax()))
                .bounds(x + 42, y, 38, 14).build();
        confirmButton = Button.builder(Component.translatable("gui.thegoods.buy.confirm"), b -> confirm())
                .bounds(x, y, 80, 16).build();
        addWidget.accept(minusButton);
        addWidget.accept(plusButton);
        addWidget.accept(maxButton);
        addWidget.accept(confirmButton);
        updateButtonStates();
        recomputeLayout();
    }

    public void open(CatalogEntry entry, long balance, int feePercent) {
        this.entry = entry;
        this.feePercent = feePercent;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
        this.affordableMax = maxAffordable(entry.stock(), balance, feePercent);
        this.quantity = Math.min(1, Math.min(entry.stock(), affordableMax));
        updateButtonStates();
        recomputeLayout();
    }

    // Shift-clicking a catalog cell: selects entry if it wasn't already, then adds a full stack of the item to
    // the quantity (capped at whatever's actually affordable/available, same as the +/- buttons' own Shift
    // behavior) — re-clicking the same already-selected item keeps adding another stack each time, cumulative.
    public void addStack(CatalogEntry entry, long balance, int feePercent) {
        boolean sameItem = this.entry != null && this.entry.item().equals(entry.item());
        if (sameItem) {
            this.entry = entry;
            this.feePercent = feePercent;
            this.affordableMax = maxAffordable(entry.stock(), balance, feePercent);
            adjustQuantity(true, displayStack.getMaxStackSize());
        } else {
            open(entry, balance, feePercent);
            setQuantity(displayStack.getMaxStackSize());
        }
    }

    // Called after a successful buy — resets to the no-selection state rather than removing any widgets, since
    // the dialog stays visible (just disabled) with nothing selected.
    public void close() {
        this.entry = null;
        this.displayStack = ItemStack.EMPTY;
        this.quantity = 0;
        this.affordableMax = 0;
        updateButtonStates();
        recomputeLayout();
    }

    // The item name (or the "no selection" placeholder) can wrap to more than one line, so the controls/receipt/
    // confirm rows below it need to move down to match rather than overlapping it — everything here is computed
    // from the wrapped line count, then applied to the buttons via setY (extractExtra uses the same controlsY for
    // the text rows it draws).
    private void recomputeLayout() {
        Component text = entry == null ? Component.translatable("gui.thegoods.buy.no_selection") : displayStack.getHoverName();
        int wrapWidth = entry == null ? NO_SELECTION_WRAP_WIDTH : ITEM_NAME_WRAP_WIDTH;
        topLines = font.split(text, wrapWidth);

        int textHeight = TEXT_TOP_PADDING + topLines.size() * font.lineHeight;
        int topHeight = Math.max(MIN_TOP_HEIGHT, textHeight);
        controlsY = y + topHeight;

        minusButton.setY(controlsY);
        plusButton.setY(controlsY);
        maxButton.setY(controlsY);
        confirmButton.setY(controlsY + CONTROLS_TO_CONFIRM);
    }

    private long affordableMax() {
        return entry == null ? 0 : Math.min(entry.stock(), affordableMax);
    }

    // Button.builder's simple OnPress callback (Button.OnPress) doesn't carry the triggering press's modifier
    // keys — only the Button#onPress(InputWithModifiers) override does — so -/+ need their own Button.Plain
    // subclass instead of the usual builder pattern. Shift = a full stack of the item (its own max stack size,
    // not always 64), Ctrl = 10, plain click = 1.
    private Button createStepButton(int bx, int by, int w, int h, String label, boolean increase) {
        Button.CreateNarration narration = supplier -> supplier.get();
        return new Button.Plain(bx, by, w, h, Component.literal(label), b -> adjustQuantity(increase, 1), narration) {
            @Override
            public void onPress(InputWithModifiers input) {
                long step = input.hasShiftDown() ? displayStack.getMaxStackSize() : input.hasControlDown() ? 10 : 1;
                adjustQuantity(increase, step);
            }
        };
    }

    private void adjustQuantity(boolean increase, long step) {
        setQuantity(quantity + (increase ? step : -step));
    }

    private void setQuantity(long newQuantity) {
        if (entry == null) {
            return;
        }
        this.quantity = Math.max(0, Math.min(newQuantity, affordableMax()));
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = entry != null;
        minusButton.active = hasSelection;
        plusButton.active = hasSelection;
        maxButton.active = hasSelection;
        confirmButton.active = hasSelection && quantity > 0;
    }

    private void confirm() {
        if (entry != null && quantity > 0) {
            ClientPacketDistributor.sendToServer(new BuyRequestPayload(entry.item(), quantity, entry.quoteHash()));
        }
    }

    // Cost isn't linear in quantity (log-based diminishing marginal cost), so binary search for the largest
    // affordable quantity rather than dividing balance by unit price.
    private static long maxAffordable(long stock, long balance, int feePercent) {
        long low = 0;
        long high = stock;
        while (low < high) {
            long mid = low + (high - low + 1) / 2;
            if (Currency.buyCostWithFee(stock, mid, feePercent) <= balance) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public void extractExtra(GuiGraphicsExtractor graphics) {
        int textX = entry == null ? x : x + ITEM_NAME_X_OFFSET;
        int lineY = y + TEXT_TOP_PADDING;
        for (FormattedCharSequence line : topLines) {
            graphics.text(font, line, textX, lineY, 0xFF404040, false);
            lineY += font.lineHeight;
        }

        if (entry == null) {
            return;
        }

        graphics.item(displayStack, x, y, 0);

        long cost = Currency.buyCostWithFee(entry.stock(), quantity, feePercent);
        String qtyText = quantity + " / " + entry.stock() + " available";
        graphics.text(font, qtyText, x, controlsY + CONTROLS_TO_QTY_TEXT, 0xFF404040, false);
        graphics.text(font, Currency.format(cost), x, controlsY + CONTROLS_TO_COST_TEXT, 0xFF7A5C00, false);
    }
}
