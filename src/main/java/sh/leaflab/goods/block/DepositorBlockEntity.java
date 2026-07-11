package sh.leaflab.goods.block;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.ItemEligibility;
import sh.leaflab.goods.economy.Stock;
import sh.leaflab.goods.registry.ModBlockEntities;

public class DepositorBlockEntity extends BlockEntity implements Container {
    private static final int SLOT_COUNT = 5;
    private static final int COOLDOWN_TICKS = 8;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private UUID owner;
    // Captured once at placement time (see setOwner) rather than resolved live from the UUID on every menu open —
    // if the owner later changes their username, the Depositor keeps showing the name they had when it was placed.
    private String ownerName;
    private int cooldown;

    public DepositorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEPOSITOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DepositorBlockEntity be) {
        if (level.isClientSide() || be.owner == null) {
            return;
        }
        if (!hasAdjacentTradeHub(level, pos)) {
            return;
        }
        if (be.cooldown > 0) {
            be.cooldown--;
            return;
        }
        be.cooldown = COOLDOWN_TICKS;
        be.processOneSlot();
    }

    private static boolean hasAdjacentTradeHub(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockState(neighbor).getBlock() instanceof TradeHubBlock) {
                return true;
            }
        }
        return false;
    }

    private void processOneSlot() {
        MinecraftServer server = getLevel().getServer();
        if (server == null) return;

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            if (!ItemEligibility.isEligible(stack)) {
                continue;
            }

            Item item = stack.getItem();
            long quantity = stack.getCount();
            long stockBefore = Stock.getStock(server, item);
            long payout = Currency.sellValue(stockBefore, quantity);

            Economy.give(server, owner, payout);
            Stock.credit(server, item, quantity);
            items.set(i, ItemStack.EMPTY);
            setChanged();
            return;
        }
    }

    public void setOwner(UUID uuid, String name) {
        this.owner = uuid;
        this.ownerName = name;
        setChanged();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getAnalogSignal() {
        int blocked = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && !ItemEligibility.isEligible(stack)) {
                blocked++;
            }
        }
        return blocked * 15 / SLOT_COUNT;
    }

    // ---- Container implementation ----

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
        if (ownerName != null) {
            output.putString("OwnerName", ownerName);
        }
        output.putInt("Cooldown", cooldown);
        ValueOutput inv = output.child("Inventory");
        for (int i = 0; i < SLOT_COUNT; i++) {
            inv.store("Slot" + i, ItemStack.OPTIONAL_CODEC, items.get(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String ownerStr = input.getStringOr("Owner", null);
        owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
        ownerName = input.getStringOr("OwnerName", null);
        cooldown = input.getIntOr("Cooldown", 0);
        ValueInput inv = input.childOrEmpty("Inventory");
        for (int i = 0; i < SLOT_COUNT; i++) {
            items.set(i, inv.read("Slot" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return super.getUpdateTag(registries);
    }
}
