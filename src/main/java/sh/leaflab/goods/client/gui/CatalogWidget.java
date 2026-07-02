package sh.leaflab.goods.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.network.CatalogEntry;
import sh.leaflab.goods.network.CatalogQueryPayload;
import sh.leaflab.goods.network.CatalogResultPayload;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

// A hand-rolled, non-Slot item grid modeled on Refined Storage's grid screen: search box with an icon at the top,
// mode-switch buttons floating outside the panel to the left, and a scrollable grid with a real scrollbar. The
// client holds the full filtered+sorted result set from the server and scrolls through it locally by row offset
// — matching RS's own client-side grid scrolling, rather than requesting a new page from the server per scroll
// tick (Milestone 6's original design).
public class CatalogWidget {
    // 9 columns matches Refined Storage's own grid.row sprite, which is natively 162px (9 * 18) wide — using
    // their column count avoids stretching/distorting the imported row background.
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 4;
    private static final int CELL_SIZE = 18;
    private static final int SEARCH_ROW_HEIGHT = 20;
    private static final int SEARCH_ICON_SIZE = 12;
    private static final int SCROLLBAR_GAP = 2;
    // Offset of the search area from the grid's left edge, when the search row shares its Y with the title —
    // matches Refined Storage's own relative offset (their search box sits at leftPos+94, their grid at
    // leftPos+8, an 86px gap).
    private static final int SEARCH_AREA_X_OFFSET = 86;
    private static final int SEARCH_TEXT_Y_OFFSET = 2;
    private static final Identifier SEARCH_ICON = SideButtonWidget.sprite("search");
    private static final Identifier ROW_BACKGROUND = SideButtonWidget.sprite("grid/row");
    private static final String[] SORT_KEYS = {"name", "price", "stock"};
    private static final int SEARCH_DEBOUNCE_TICKS = 10;

    private final int x;
    private final int y;
    private final int sideButtonX;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<GuiEventListener> removeWidget;
    private final Consumer<CatalogEntry> onSelect;

    private EditBox searchBox;
    private ScrollbarWidget scrollbar;
    private final List<CatalogCellWidget> cells = new ArrayList<>();
    private List<CatalogEntry> allEntries = Collections.emptyList();

    private int sortIndex;
    private boolean ascending = true;
    private int feePercent;
    private int searchDebounceTicks = -1;

    public CatalogWidget(int x, int y, int sideButtonX, Font font,
                          Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget, Consumer<CatalogEntry> onSelect) {
        this.x = x;
        this.y = y;
        this.sideButtonX = sideButtonX;
        this.font = font;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.onSelect = onSelect;
    }

    public void init() {
        int searchBoxX = searchBoxX();
        // Text/cursor Y is offset a couple pixels below the hand-drawn background box's own Y (still just `y` in
        // extractExtra) — the box and title are aligned correctly, but the EditBox's own text+cursor render a
        // little high within it otherwise (vanilla ties cursor position to the same textY as the text itself, so
        // this nudges both together).
        searchBox = new EditBox(font, searchBoxX, y + SEARCH_TEXT_Y_OFFSET, searchAreaRight() - searchBoxX, 14, Component.translatable("gui.thegoods.catalog.search"));
        // Bordered EditBox vertically centers its text within its height (getY() + (height-8)/2), which is what
        // was throwing the search bar's text out of alignment with the title's plain graphics.text() draw at the
        // same Y. Unbordered draws text at exactly getY(), matching the title — same fix Refined Storage's own
        // SearchFieldWidget uses (it calls setBordered(false) too).
        searchBox.setBordered(false);
        searchBox.setResponder(value -> searchDebounceTicks = SEARCH_DEBOUNCE_TICKS);
        addWidget.accept(searchBox);

        // Side buttons float outside the panel to the left, stacked top-down — same positioning idiom as
        // Refined Storage's AbstractBaseScreen#addSideButton.
        int sideButtonY = y;
        addWidget.accept(new SideButtonWidget(sideButtonX, sideButtonY, font,
                this::sortTypeIcon, () -> "$", this::sortTypeTooltip, this::cycleSortType));
        sideButtonY += SideButtonWidget.SIZE + 2;
        addWidget.accept(new SideButtonWidget(sideButtonX, sideButtonY, font,
                this::sortDirectionIcon, () -> ascending ? "^" : "v", this::sortDirectionTooltip, this::toggleSortDirection));

        scrollbar = new ScrollbarWidget(x + gridWidth() + SCROLLBAR_GAP, gridY(), GRID_ROWS * CELL_SIZE);
        scrollbar.setListener(offset -> rebuildVisibleCells());
        addWidget.accept(scrollbar);

        sendQuery();
    }

    private int gridWidth() {
        return GRID_COLS * CELL_SIZE;
    }

    private int gridY() {
        return y + SEARCH_ROW_HEIGHT;
    }

    // The search row shares its Y with the screen title, so it starts well to the right of the title text rather
    // than at the grid's own left edge — see SEARCH_AREA_X_OFFSET.
    private int searchIconX() {
        return x + SEARCH_AREA_X_OFFSET;
    }

    private int searchBoxX() {
        return searchIconX() + SEARCH_ICON_SIZE + 4;
    }

    private int searchAreaRight() {
        return x + gridWidth() + SCROLLBAR_GAP + 12;
    }

    public void tick() {
        if (searchDebounceTicks > 0) {
            searchDebounceTicks--;
            if (searchDebounceTicks == 0) {
                sendQuery();
            }
        }
    }

    // EditBox#keyPressed only consumes special keys (backspace, arrows, paste...) and returns false for plain
    // printable characters, which are handled separately by charTyped — so on its own, typing "e" while the
    // search box is focused falls through to AbstractContainerScreen's inventory-close keybind check and closes
    // the whole screen. Vanilla's CreativeModeInventoryScreen has the exact same search-box-in-a-container-screen
    // situation and guards it the same way: swallow the key whenever the search box is focused, unless it's Escape.
    public boolean searchConsumesKey(KeyEvent event) {
        if (searchBox == null) {
            return false;
        }
        if (searchBox.keyPressed(event)) {
            return true;
        }
        return searchBox.isFocused() && searchBox.isVisible() && !event.isEscape();
    }

    // Lets the screen forward wheel scroll from anywhere over the grid (not just the thin scrollbar strip) to the
    // scrollbar — matching how RS lets you scroll the grid without needing to hover the scrollbar precisely.
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        boolean overGrid = mouseX >= x && mouseX <= x + gridWidth() + SCROLLBAR_GAP + 12
                && mouseY >= gridY() && mouseY <= gridY() + GRID_ROWS * CELL_SIZE;
        if (overGrid) {
            scrollbar.scrollByWheel(scrollY);
            return true;
        }
        return false;
    }

    private Identifier sortTypeIcon() {
        return switch (SORT_KEYS[sortIndex]) {
            case "name" -> SideButtonWidget.sprite("widget/side_button/grid/sorting_type/name");
            case "stock" -> SideButtonWidget.sprite("widget/side_button/grid/sorting_type/quantity");
            default -> null;
        };
    }

    private Identifier sortDirectionIcon() {
        return ascending
                ? SideButtonWidget.sprite("widget/side_button/grid/sorting_direction/ascending")
                : SideButtonWidget.sprite("widget/side_button/grid/sorting_direction/descending");
    }

    private Component sortTypeTooltip() {
        return Component.translatable("gui.thegoods.catalog.sort_type." + SORT_KEYS[sortIndex]);
    }

    private Component sortDirectionTooltip() {
        return Component.translatable(ascending ? "gui.thegoods.catalog.sort_direction.ascending" : "gui.thegoods.catalog.sort_direction.descending");
    }

    private void cycleSortType() {
        sortIndex = (sortIndex + 1) % SORT_KEYS.length;
        sendQuery();
    }

    private void toggleSortDirection() {
        ascending = !ascending;
        sendQuery();
    }

    private void sendQuery() {
        String search = searchBox != null ? searchBox.getValue() : "";
        ClientPacketDistributor.sendToServer(new CatalogQueryPayload(search, SORT_KEYS[sortIndex], ascending));
    }

    public void onResult(CatalogResultPayload result) {
        this.allEntries = result.entries();
        this.feePercent = result.feePercent();

        int totalRows = (allEntries.size() + GRID_COLS - 1) / GRID_COLS;
        scrollbar.configure(totalRows, GRID_ROWS);
        rebuildVisibleCells();
    }

    private void rebuildVisibleCells() {
        for (CatalogCellWidget cell : cells) {
            removeWidget.accept(cell);
        }
        cells.clear();

        int firstRow = (int) scrollbar.getOffset();
        int start = firstRow * GRID_COLS;
        int end = Math.min(start + GRID_ROWS * GRID_COLS, allEntries.size());
        for (int i = start; i < end; i++) {
            int indexInView = i - start;
            int col = indexInView % GRID_COLS;
            int row = indexInView / GRID_COLS;
            CatalogCellWidget cell = new CatalogCellWidget(x + col * CELL_SIZE, gridY() + row * CELL_SIZE, allEntries.get(i), font, onSelect);
            cells.add(cell);
            addWidget.accept(cell);
        }
    }

    public int getFeePercent() {
        return feePercent;
    }

    public void extractExtra(GuiGraphicsExtractor graphics) {
        // RS has no standalone search-box sprite — their box has no visible border at all (setBordered(false)
        // like ours) because it's drawn over a search-box-shaped cutout baked into their full panel texture,
        // which this mod doesn't have. Without any background this box is invisible, so give it a plain
        // hand-drawn recessed well instead, matching the bevel style used for the Sell Slot and grid slots.
        int boxX = searchBoxX();
        int boxRight = searchAreaRight();
        graphics.fill(boxX - 2, y - 1, boxRight, y + 15, 0xFF373737);
        graphics.fill(boxX - 2, y - 1, boxRight - 1, y + 14, 0xFFFFFFFF);
        graphics.fill(boxX - 1, y, boxRight - 1, y + 14, 0xFF8B8B8B);

        graphics.blitSprite(GUI_TEXTURED, SEARCH_ICON, searchIconX(), y + 1, SEARCH_ICON_SIZE, SEARCH_ICON_SIZE);
        for (int row = 0; row < GRID_ROWS; row++) {
            graphics.blitSprite(GUI_TEXTURED, ROW_BACKGROUND, x, gridY() + row * CELL_SIZE, gridWidth(), CELL_SIZE);
        }
    }
}
