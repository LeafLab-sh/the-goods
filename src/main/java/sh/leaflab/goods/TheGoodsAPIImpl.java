package sh.leaflab.goods;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import sh.leaflab.goods.api.ITheGoodsAPI;
import sh.leaflab.goods.api.TradeRequest;
import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.ItemEligibility;
import sh.leaflab.goods.economy.Stock;

public final class TheGoodsAPIImpl implements ITheGoodsAPI {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private static volatile ITheGoodsAPI instance;

    public static void init() {
        instance = new TheGoodsAPIImpl();
        ITheGoodsAPI.set(instance);
    }

    @Override
    public long getBalance(MinecraftServer server, UUID player) {
        return Economy.getBalance(server, player);
    }

    @Override
    public long giveCurrency(MinecraftServer server, UUID player, long amount) {
        Economy.give(server, NIL_UUID, player, amount);
        return Economy.getBalance(server, player);
    }

    @Override
    public long takeCurrency(MinecraftServer server, UUID player, long amount) {
        Economy.take(server, NIL_UUID, player, amount);
        return Economy.getBalance(server, player);
    }

    @Override
    public void resetBalance(MinecraftServer server, UUID player) {
        Economy.reset(server, NIL_UUID, player);
    }

    @Override
    public boolean transferCurrency(MinecraftServer server, UUID from, UUID to, long amount) {
        if (Economy.getBalance(server, from) < amount) {
            return false;
        }
        Economy.transfer(server, from, to, amount);
        return true;
    }

    @Override
    public long getTotalCirculation(MinecraftServer server) {
        return Economy.totalCirculation(server);
    }

    @Override
    public long getLifetimeFees(MinecraftServer server) {
        return Economy.getLifetimeFees(server);
    }

    @Override
    public long getStock(MinecraftServer server, Item item) {
        return Stock.getStock(server, item);
    }

    @Override
    public long creditStock(MinecraftServer server, Item item, long quantity) {
        Stock.credit(server, item, quantity);
        return Stock.getStock(server, item);
    }

    @Override
    public long debitStock(MinecraftServer server, Item item, long quantity) {
        Stock.debit(server, item, quantity);
        return Stock.getStock(server, item);
    }

    @Override
    public Map<Item, Long> getAllStock(MinecraftServer server) {
        return Stock.positiveStock(server);
    }

    @Override
    public boolean isEligible(ItemStack stack) {
        return ItemEligibility.isEligible(stack);
    }

    @Override
    public long calculateSellValue(long stockBefore, long quantity) {
        return Currency.sellValue(stockBefore, quantity);
    }

    @Override
    public long calculateBuyCost(long stockBefore, long quantity) {
        return Currency.buyCost(stockBefore, quantity);
    }

    @Override
    public long calculateBuyCostWithFee(long stockBefore, long quantity, int feePercent) {
        return Currency.buyCostWithFee(stockBefore, quantity, feePercent);
    }

    @Override
    public Optional<TradeRequest> findPendingRequest(MinecraftServer server, UUID requester, UUID payer) {
        return Economy.findRequest(server, requester, payer);
    }

    @Override
    public int getTransactionFeePercent() {
        return Config.TRANSACTION_FEE_PERCENT.get();
    }

    @Override
    public String getCurrencyName() {
        return Config.CURRENCY_NAME.get();
    }

    @Override
    public boolean hasOpLevel(ServerPlayer player, int level) {
        var check = switch (level) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            case 4 -> Commands.LEVEL_OWNERS;
            default -> throw new IllegalArgumentException("Invalid permission level: " + level);
        };
        return check.check(player.permissions());
    }

    @Override
    public boolean hasGamemasterPermission(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    @Override
    public boolean hasAdminPermission(ServerPlayer player) {
        return Commands.LEVEL_OWNERS.check(player.permissions());
    }
}
