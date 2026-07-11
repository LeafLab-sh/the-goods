package sh.leaflab.goods.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.block.DepositorBlockEntity;
import sh.leaflab.goods.registry.ModMenuTypes;

public class DepositorMenu extends AbstractContainerMenu {
    private static final int DEPOSITOR_SLOT_COUNT = 5;
    private static final int DEPOSITOR_SLOTS_START = 0;
    private static final int PLAYER_INV_START = DEPOSITOR_SLOT_COUNT;
    private static final int HOTBAR_START = PLAYER_INV_START + 27;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    // Shared with DepositorScreen (imageHeight/inventoryLabelY/row backgrounds) — same idiom as
    // TradeHubMenu.PLAYER_INVENTORY_Y, so the menu's actual slot layout is the single source of truth.
    public static final int PLAYER_INVENTORY_Y = 52;

    private final Container depositContainer;

    // Server-side constructor.
    public DepositorMenu(int containerId, Inventory playerInventory, DepositorBlockEntity be) {
        this(containerId, playerInventory, (Container) be);
    }

    // Client-side constructor (from IMenuTypeExtension).
    public DepositorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(DEPOSITOR_SLOT_COUNT));
    }

    private DepositorMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.DEPOSITOR.get(), containerId);
        this.depositContainer = container;

        for (int i = 0; i < DEPOSITOR_SLOT_COUNT; i++) {
            this.addSlot(new Slot(container, i, 44 + i * 18, 20));
        }

        this.addStandardInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < DEPOSITOR_SLOT_COUNT) {
            if (!this.moveItemStackTo(original, PLAYER_INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, DEPOSITOR_SLOTS_START, DEPOSITOR_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return depositContainer.stillValid(player);
    }
}
