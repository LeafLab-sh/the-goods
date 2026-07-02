package sh.leaflab.goods.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.menu.TradeHubMenu;
import sh.leaflab.goods.network.SellDecisionPayload;
import sh.leaflab.goods.network.SetSellDialogModePayload;

// A price line + balance + Confirm/Cancel row below the Sell Slot, relevant only in Sell Dialog mode
// (stage-then-confirm) — the mode toggle itself is a floating SideButtonWidget built by TradeHubScreen, same
// idiom as the catalog's sort-mode buttons, not owned here. Confirm/Cancel are only added to the screen at all
// while Sell Dialog mode is active (not just disabled otherwise) — Quick Sell mode shouldn't show sell-dialog
// controls it has no use for. Sale price is drawn above balance here (the reverse of Quick Sell mode's
// balance-then-nothing layout, since the whole point of staging is to see what you'd get before your balance
// updates) — a fixed one-line gap is reserved there whether or not anything's staged, so balance/Confirm/Cancel
// don't jump position as items get staged/unstaged. The staged item itself lives in the real Sell Slot (synced
// to the client via vanilla's normal menu slot sync, not a custom payload); only the live stock figure needed to
// compute the payout preview comes over SellPreviewPayload.
public class SellDialog {
    private static final int GAP = 4;
    private static final int CURRENCY_COLOR = 0xFF7A5C00;

    private final int x;
    private final int y;
    private final int balanceY;
    private final int controlsY;
    private final Font font;
    private final TradeHubMenu menu;
    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<GuiEventListener> removeWidget;

    private Button confirmButton;
    private Button cancelButton;
    private boolean controlsShown;

    public SellDialog(int x, int y, Font font, TradeHubMenu menu, Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget) {
        this.x = x;
        this.y = y;
        this.font = font;
        this.menu = menu;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.balanceY = y + font.lineHeight + GAP;
        this.controlsY = balanceY + font.lineHeight + GAP;
    }

    // No open()/close() hooks to react to here — mode and staged state can both change from outside this class
    // (a player's own slot interaction, or the floating toggle button), so this is just checked every client tick.
    public void tick() {
        boolean sellDialogMode = menu.isSellDialogEnabled();
        if (sellDialogMode && !controlsShown) {
            confirmButton = Button.builder(Component.translatable("gui.thegoods.buy.confirm"), b -> sendDecision(true))
                    .bounds(x, controlsY, 40, 14).build();
            cancelButton = Button.builder(Component.translatable("gui.thegoods.sell.cancel"), b -> sendDecision(false))
                    .bounds(x + 44, controlsY, 40, 14).build();
            addWidget.accept(confirmButton);
            addWidget.accept(cancelButton);
            controlsShown = true;
        } else if (!sellDialogMode && controlsShown) {
            removeWidget.accept(confirmButton);
            removeWidget.accept(cancelButton);
            confirmButton = null;
            cancelButton = null;
            controlsShown = false;
        }

        if (controlsShown) {
            boolean hasStaged = !stagedItem().isEmpty();
            confirmButton.active = hasStaged;
            cancelButton.active = hasStaged;
        }
    }

    private ItemStack stagedItem() {
        return menu.getStagedSellItem();
    }

    public String modeIconText() {
        return menu.isSellDialogEnabled() ? "D" : "Q";
    }

    public Component modeTooltip() {
        return Component.translatable(menu.isSellDialogEnabled() ? "gui.thegoods.sell.mode_dialog" : "gui.thegoods.sell.mode_quick");
    }

    public void toggleMode() {
        boolean newState = !menu.isSellDialogEnabled();
        // Update the client's own menu instance immediately (for consistent client-side SellSlot prediction —
        // menu logic runs mirrored on both sides) rather than waiting on a server round-trip to take effect.
        menu.handleSellDialogModeChange(newState);
        ClientPacketDistributor.sendToServer(new SetSellDialogModePayload(newState));
    }

    private void sendDecision(boolean confirm) {
        ClientPacketDistributor.sendToServer(new SellDecisionPayload(confirm));
    }

    public void extractExtra(GuiGraphicsExtractor graphics) {
        if (!menu.isSellDialogEnabled()) {
            return;
        }
        ItemStack staged = stagedItem();
        if (!staged.isEmpty()) {
            // Full precision, not the abbreviated K/M/G scheme — this is a trade about to be confirmed, same
            // rule as the Buy Dialog's own receipt (see docs/spec.md Display formatting).
            long payout = Currency.sellValue(menu.getClientSellPreviewStock(), staged.getCount());
            graphics.text(font, Currency.format(payout), x, y, CURRENCY_COLOR, false);
        }

        String balanceText = Currency.format(menu.getClientBalance());
        graphics.text(font, balanceText, x, balanceY, CURRENCY_COLOR, false);
    }
}
