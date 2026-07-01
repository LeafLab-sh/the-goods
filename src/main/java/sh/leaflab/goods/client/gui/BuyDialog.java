package sh.leaflab.goods.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.network.BuyRequestPayload;
import sh.leaflab.goods.network.CatalogEntry;

// Embedded panel shown when a catalog cell is clicked, rather than a separate popup Screen — +/-/max quantity and
// a live receipt computed with the exact same Currency methods the server charges with, so the preview can never
// disagree with the actual charge. Confirm sends the buy request with the quote hash echoed back; the server
// re-derives price from live stock regardless of anything computed here.
public class BuyDialog {
    private final int x;
    private final int y;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<GuiEventListener> removeWidget;

    private CatalogEntry entry;
    private ItemStack displayStack = ItemStack.EMPTY;
    private long quantity;
    private long affordableMax;
    private int feePercent;
    private Button confirmButton;
    private Button minusButton;
    private Button plusButton;
    private Button maxButton;
    private boolean open;

    public BuyDialog(int x, int y, Font font, Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget) {
        this.x = x;
        this.y = y;
        this.font = font;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
    }

    public void open(CatalogEntry entry, long balance, int feePercent) {
        close();
        this.entry = entry;
        this.feePercent = feePercent;
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.AIR);
        this.displayStack = new ItemStack(item);
        this.affordableMax = maxAffordable(entry.stock(), balance, feePercent);
        this.quantity = Math.min(1, Math.min(entry.stock(), affordableMax));
        this.open = true;

        minusButton = Button.builder(Component.literal("-"), b -> setQuantity(quantity - 1)).bounds(x, y + 18, 20, 14).build();
        plusButton = Button.builder(Component.literal("+"), b -> setQuantity(quantity + 1)).bounds(x + 24, y + 18, 20, 14).build();
        maxButton = Button.builder(Component.translatable("gui.thegoods.buy.max"), b -> setQuantity(Math.min(entry.stock(), affordableMax)))
                .bounds(x + 48, y + 18, 40, 14).build();
        confirmButton = Button.builder(Component.translatable("gui.thegoods.buy.confirm"), b -> confirm()).bounds(x + 96, y + 18, 86, 14).build();
        addWidget.accept(minusButton);
        addWidget.accept(plusButton);
        addWidget.accept(maxButton);
        addWidget.accept(confirmButton);
        updateConfirmState();
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        for (Button button : new Button[] {minusButton, plusButton, maxButton, confirmButton}) {
            if (button != null) {
                removeWidget.accept(button);
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    private void setQuantity(long newQuantity) {
        this.quantity = Math.max(0, Math.min(newQuantity, Math.min(entry.stock(), affordableMax)));
        updateConfirmState();
    }

    private void updateConfirmState() {
        if (confirmButton != null) {
            confirmButton.active = quantity > 0;
        }
    }

    private void confirm() {
        if (quantity > 0) {
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
        if (!open) {
            return;
        }
        graphics.item(displayStack, x, y, 0);
        graphics.text(font, displayStack.getHoverName(), x + 20, y + 5, 0xFF404040, false);

        long cost = Currency.buyCostWithFee(entry.stock(), quantity, feePercent);
        String qtyText = quantity + " / " + entry.stock() + " available";
        graphics.text(font, qtyText, x, y + 36, 0xFF404040, false);
        graphics.text(font, Currency.format(cost), x, y + 48, 0xFF7A5C00, false);
    }
}
