package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.menu.TradeHubMenu;
import sh.leaflab.goods.network.BuyResultPayload;
import sh.leaflab.goods.network.CatalogResultPayload;

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

    private static final int CATALOG_X = 8;
    private static final int CATALOG_Y = 20;
    private static final int BUY_DIALOG_X = 8;
    private static final int BUY_DIALOG_Y = 136;

    private CatalogWidget catalogWidget;
    private BuyDialog buyDialog;
    private CatalogResultPayload lastSeenCatalogResult;
    private BuyResultPayload lastSeenBuyResult;

    public TradeHubScreen(TradeHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 210, 280);
    }

    @Override
    protected void init() {
        super.init();
        catalogWidget = new CatalogWidget(
                this.leftPos + CATALOG_X, this.topPos + CATALOG_Y, this.font,
                this::addRenderableWidget, this::removeWidget, this::onCatalogEntrySelected);
        catalogWidget.init();
        buyDialog = new BuyDialog(
                this.leftPos + BUY_DIALOG_X, this.topPos + BUY_DIALOG_Y, this.font,
                this::addRenderableWidget, this::removeWidget);
    }

    private void onCatalogEntrySelected(sh.leaflab.goods.network.CatalogEntry entry) {
        buyDialog.open(entry, this.menu.getClientBalance(), catalogWidget.getFeePercent());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        catalogWidget.tick();

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

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);
        for (Slot slot : this.menu.slots) {
            extractSlotWell(graphics, this.leftPos + slot.x, this.topPos + slot.y);
        }
        catalogWidget.extractExtra(graphics);
        buyDialog.extractExtra(graphics);
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
