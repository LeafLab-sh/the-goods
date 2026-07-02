package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.menu.TradeHubMenu;
import sh.leaflab.goods.network.BuyResultPayload;
import sh.leaflab.goods.network.CatalogResultPayload;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

// Plain-color panel, not a textured background — a placeholder like the block texture from Milestone 1, easy to
// swap for real art later without touching layout/interaction logic. The catalog grid and player inventory use
// Refined Storage's actual slot-row sprite (imported under REFINEDSTORAGE2_LICENSE.txt); the Sell Slot — not part
// of either 9-wide row — keeps a hand-drawn bevel since there's no RS equivalent for a single isolated slot.
public class TradeHubScreen extends AbstractContainerScreen<TradeHubMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int SLOT_WELL_COLOR = 0xFF8B8B8B;
    private static final int SLOT_BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BEVEL_DARK = 0xFF373737;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int BALANCE_COLOR = 0xFF7A5C00;
    private static final Identifier ROW_BACKGROUND = SideButtonWidget.sprite("grid/row");
    private static final int INVENTORY_ROW_WIDTH = 9 * 18;

    // The search bar shares the top row with the title, where the balance readout used to sit (RS-style: their
    // own search box shares the top row with other GUI chrome too). Below the grid, the middle section is split
    // left/right by a vertical divider: Sell heading + Sell Slot + balance on the left, the Buy interface on the
    // right — see TradeHubMenu for the shared Sell Slot Y constant.
    private static final int CATALOG_X = 8;
    private static final int CATALOG_Y = 6;
    private static final int MIDDLE_TOP = 102;
    private static final int MIDDLE_BOTTOM = TradeHubMenu.PLAYER_INVENTORY_Y - 2;
    private static final int DIVIDER_X = 96;
    private static final int SELL_HEADING_X = 8;
    private static final int BALANCE_X = 8;
    private static final int BALANCE_Y = TradeHubMenu.SELL_SLOT_Y + 18 + 6;
    private static final int SELL_DIALOG_X = 8;
    // Same Y as BALANCE_Y — only one of the two is actually drawn at a time, depending on mode: Quick Sell draws
    // balance here directly (extractLabels), Sell Dialog mode hands this whole area to SellDialog instead, which
    // draws price/placeholder first and balance below it (swapped order — see SellDialog).
    private static final int SELL_DIALOG_Y = BALANCE_Y;
    // No "Buy" heading (removed — the right side has no heading, so its content starts right at MIDDLE_TOP).
    private static final int BUY_DIALOG_X = DIVIDER_X + 4;
    private static final int BUY_DIALOG_Y = MIDDLE_TOP;
    private static final Component SELL_HEADING = Component.translatable("gui.thegoods.trade_hub.sell_heading");

    private CatalogWidget catalogWidget;
    private BuyDialog buyDialog;
    private SellDialog sellDialog;
    private CatalogResultPayload lastSeenCatalogResult;
    private BuyResultPayload lastSeenBuyResult;

    public TradeHubScreen(TradeHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 192, 276);
        // AbstractContainerScreen defaults titleLabelY to 6, same as the search row's Y — but the two still don't
        // read as aligned, so nudge the title down a couple pixels to visually match.
        this.titleLabelY = 9;
    }

    @Override
    protected void init() {
        super.init();
        // Side buttons float outside the panel to the left, like Refined Storage's grid screen — needs leftPos
        // itself, not a position relative to the panel's own content.
        int sideButtonX = this.leftPos - SideButtonWidget.SIZE - 2;
        catalogWidget = new CatalogWidget(
                this.leftPos + CATALOG_X, this.topPos + CATALOG_Y, sideButtonX, this.font,
                this::addRenderableWidget, this::removeWidget, this::onCatalogEntrySelected);
        catalogWidget.init();
        buyDialog = new BuyDialog(this.leftPos + BUY_DIALOG_X, this.topPos + BUY_DIALOG_Y, this.font, this::addRenderableWidget);
        sellDialog = new SellDialog(this.leftPos + SELL_DIALOG_X, this.topPos + SELL_DIALOG_Y, this.font, this.menu, this::addRenderableWidget, this::removeWidget);
        // Sell mode toggle floats outside the panel to the left, aligned with the Sell Slot — same idiom as the
        // catalog's sort-mode side buttons, just its own separate column position rather than stacked with them.
        addRenderableWidget(new SideButtonWidget(sideButtonX, this.topPos + TradeHubMenu.SELL_SLOT_Y, this.font,
                () -> null, sellDialog::modeIconText, sellDialog::modeTooltip, sellDialog::toggleMode));
    }

    // Shift-clicking a cell in the catalog ("the store inventory," per docs/spec.md) adds a full stack to the
    // Buy quantity instead of just selecting the item — same "Shift = a full stack" convention as the +/-
    // buttons, just triggered from the grid itself as a shortcut.
    private void onCatalogEntrySelected(sh.leaflab.goods.network.CatalogEntry entry, boolean shiftHeld) {
        if (shiftHeld) {
            buyDialog.addStack(entry, this.menu.getClientBalance(), catalogWidget.getFeePercent());
        } else {
            buyDialog.open(entry, this.menu.getClientBalance(), catalogWidget.getFeePercent());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        catalogWidget.tick();
        sellDialog.tick();

        CatalogResultPayload catalogResult = this.menu.getClientCatalogResult();
        if (catalogResult != null && catalogResult != lastSeenCatalogResult) {
            lastSeenCatalogResult = catalogResult;
            catalogWidget.onResult(catalogResult);
        }

        BuyResultPayload buyResult = this.menu.getClientLastBuyResult();
        if (buyResult != null && buyResult != lastSeenBuyResult) {
            lastSeenBuyResult = buyResult;
            if (buyResult.success()) {
                buyDialog.close();
            }
        }
    }

    // Without this, typing a plain letter (e.g. the default inventory-close key "E") while the search box has
    // focus falls through AbstractContainerScreen's default keyPressed to the keyInventory keybind check and
    // closes the whole screen — see CatalogWidget#searchConsumesKey for why. Must be checked before delegating to
    // super, since AbstractContainerScreen.keyPressed would already call onClose() as a side effect by then.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (catalogWidget != null && catalogWidget.searchConsumesKey(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    // Lets the mouse wheel scroll the catalog grid from anywhere over it, not just the thin scrollbar strip —
    // checked before super so it isn't swallowed by (or fights with) default container scroll handling.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (catalogWidget != null && catalogWidget.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);

        // Sunken groove (dark then light line) splitting the middle section into a Sell side (left) and a Buy
        // side (right) — same two-tone bevel idiom as the slot wells, just a straight line instead of a box.
        int dividerX = this.leftPos + DIVIDER_X;
        graphics.fill(dividerX, this.topPos + MIDDLE_TOP, dividerX + 1, this.topPos + MIDDLE_BOTTOM, SLOT_BEVEL_DARK);
        graphics.fill(dividerX + 1, this.topPos + MIDDLE_TOP, dividerX + 2, this.topPos + MIDDLE_BOTTOM, SLOT_BEVEL_LIGHT);

        extractSlotWell(graphics, this.leftPos + TradeHubMenu.SELL_SLOT_X, this.topPos + TradeHubMenu.SELL_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            int rowY = this.topPos + TradeHubMenu.PLAYER_INVENTORY_Y + row * 18;
            graphics.blitSprite(GUI_TEXTURED, ROW_BACKGROUND, this.leftPos + 8, rowY, INVENTORY_ROW_WIDTH, 18);
        }
        graphics.blitSprite(GUI_TEXTURED, ROW_BACKGROUND, this.leftPos + 8, this.topPos + TradeHubMenu.PLAYER_INVENTORY_Y + 58, INVENTORY_ROW_WIDTH, 18);

        catalogWidget.extractExtra(graphics);
        buyDialog.extractExtra(graphics);
        sellDialog.extractExtra(graphics);
    }

    // An 18x18 cell around a slot's 16x16 item area: dark edge at bottom/right, light edge at top/left, mid-gray
    // fill — the classic vanilla "sunken" slot bevel, drawn with three overlapping fills instead of a texture.
    // Only used for the Sell Slot now — the catalog grid and player inventory use the RS row sprite instead.
    private static void extractSlotWell(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BEVEL_DARK);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_BEVEL_LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_WELL_COLOR);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);

        graphics.text(this.font, SELL_HEADING, SELL_HEADING_X, MIDDLE_TOP, TITLE_COLOR, false);
        // In Sell Dialog mode, SellDialog draws balance itself (below the price/placeholder line — swapped
        // order) instead of this fixed spot.
        if (!this.menu.isSellDialogEnabled()) {
            String balanceText = Currency.format(this.menu.getClientBalance());
            graphics.text(this.font, balanceText, BALANCE_X, BALANCE_Y, BALANCE_COLOR, false);
        }

        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TITLE_COLOR, false);
    }
}
