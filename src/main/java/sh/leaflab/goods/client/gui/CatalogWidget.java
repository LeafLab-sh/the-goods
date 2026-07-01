package sh.leaflab.goods.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.network.CatalogEntry;
import sh.leaflab.goods.network.CatalogQueryPayload;
import sh.leaflab.goods.network.CatalogResultPayload;

// A hand-rolled, non-Slot item grid (search box, server-sorted Name/Price/Stock, paginated), mirroring the
// AE2-terminal/JEI-list pattern the design doc calls for — a vanilla Slot-per-item approach doesn't scale to a
// catalog of arbitrary size. Pagination (Prev/Next) is used instead of a continuous drag-scrollbar to keep the
// hit-testing simple; still lets you browse a catalog of any size.
public class CatalogWidget {
    private static final int GRID_COLS = 7;
    private static final int GRID_ROWS = 4;
    private static final int CELL_SIZE = 18;
    private static final int SEARCH_ROW_HEIGHT = 20;
    private static final String[] SORT_KEYS = {"name", "price", "stock"};
    private static final String[] SORT_LABELS = {"Name", "Price", "Stock"};
    private static final int SEARCH_DEBOUNCE_TICKS = 10;

    private final int x;
    private final int y;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<GuiEventListener> removeWidget;
    private final Consumer<CatalogEntry> onSelect;

    private EditBox searchBox;
    private Button sortButton;
    private final List<CatalogCellWidget> cells = new ArrayList<>();

    private int sortIndex;
    private boolean ascending = true;
    private int page;
    private int totalPages = 1;
    private int feePercent;
    private int searchDebounceTicks = -1;

    public CatalogWidget(int x, int y, Font font, Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget, Consumer<CatalogEntry> onSelect) {
        this.x = x;
        this.y = y;
        this.font = font;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.onSelect = onSelect;
    }

    public void init() {
        searchBox = new EditBox(font, x, y, 118, 14, Component.translatable("gui.thegoods.catalog.search"));
        searchBox.setResponder(value -> searchDebounceTicks = SEARCH_DEBOUNCE_TICKS);
        addWidget.accept(searchBox);

        sortButton = Button.builder(sortLabel(), btn -> cycleSort()).bounds(x + 122, y, 62, 14).build();
        addWidget.accept(sortButton);

        int pageRowY = gridY() + GRID_ROWS * CELL_SIZE + 4;
        addWidget.accept(Button.builder(Component.literal("<"), btn -> changePage(-1)).bounds(x, pageRowY, 40, 14).build());
        addWidget.accept(Button.builder(Component.literal(">"), btn -> changePage(1)).bounds(x + 144, pageRowY, 40, 14).build());

        sendQuery();
    }

    private int gridY() {
        return y + SEARCH_ROW_HEIGHT;
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

    public void tick() {
        if (searchDebounceTicks > 0) {
            searchDebounceTicks--;
            if (searchDebounceTicks == 0) {
                page = 0;
                sendQuery();
            }
        }
    }

    private Component sortLabel() {
        return Component.literal(SORT_LABELS[sortIndex] + (ascending ? " ▲" : " ▼"));
    }

    private void cycleSort() {
        if (ascending) {
            ascending = false;
        } else {
            ascending = true;
            sortIndex = (sortIndex + 1) % SORT_KEYS.length;
        }
        sortButton.setMessage(sortLabel());
        page = 0;
        sendQuery();
    }

    private void changePage(int delta) {
        int newPage = page + delta;
        if (newPage < 0 || newPage >= totalPages) {
            return;
        }
        page = newPage;
        sendQuery();
    }

    private void sendQuery() {
        String search = searchBox != null ? searchBox.getValue() : "";
        ClientPacketDistributor.sendToServer(new CatalogQueryPayload(search, SORT_KEYS[sortIndex], ascending, page));
    }

    public void onResult(CatalogResultPayload result) {
        this.page = result.page();
        this.totalPages = result.totalPages();
        this.feePercent = result.feePercent();

        for (CatalogCellWidget cell : cells) {
            removeWidget.accept(cell);
        }
        cells.clear();

        List<CatalogEntry> entries = result.entries();
        for (int i = 0; i < entries.size(); i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            CatalogCellWidget cell = new CatalogCellWidget(x + col * CELL_SIZE, gridY() + row * CELL_SIZE, entries.get(i), onSelect);
            cells.add(cell);
            addWidget.accept(cell);
        }
    }

    public int getFeePercent() {
        return feePercent;
    }

    public void extractExtra(GuiGraphicsExtractor graphics) {
        String pageText = (page + 1) + "/" + totalPages;
        int pageRowY = gridY() + GRID_ROWS * CELL_SIZE + 4;
        int gapStart = x + 40;
        int gapWidth = 104;
        int textWidth = font.width(pageText);
        graphics.text(font, pageText, gapStart + (gapWidth - textWidth) / 2, pageRowY + 3, 0xFF404040, false);
    }
}
