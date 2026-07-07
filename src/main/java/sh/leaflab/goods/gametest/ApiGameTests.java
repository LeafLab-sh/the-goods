package sh.leaflab.goods.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.TheGoods;
import sh.leaflab.goods.api.ITheGoodsAPI;
import sh.leaflab.goods.economy.Economy;

public final class ApiGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, TheGoods.MODID);

    private static final int MAX_TICKS = 40;

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> API_SINGLETON_AVAILABLE =
            register("api_singleton_available", ApiGameTests::apiSingletonAvailable);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> API_BALANCE_READ_WRITE =
            register("api_balance_read_write", ApiGameTests::apiBalanceReadWrite);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> API_ITEM_ELIGIBILITY =
            register("api_item_eligibility", ApiGameTests::apiItemEligibility);

    private static DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> register(String name, Consumer<GameTestHelper> test) {
        return TEST_FUNCTIONS.register(name, () -> test);
    }

    public static void register(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(TheGoods.MODID, "api"));
        Identifier emptyStructure = Identifier.withDefaultNamespace("empty");
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> test : List.of(
                API_SINGLETON_AVAILABLE, API_BALANCE_READ_WRITE, API_ITEM_ELIGIBILITY)) {
            event.registerTest(test.getId(), new FunctionGameTestInstance(
                    test.getKey(), new TestData<>(environment, emptyStructure, MAX_TICKS, 0, true)));
        }
    }

    private static void apiSingletonAvailable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ITheGoodsAPI api = ITheGoodsAPI.get();
        helper.assertFalse(api == null, "API singleton must not be null");
        helper.assertTrue(api.getBalance(server, UUID.randomUUID()) >= 0L,
                "balance query should not crash");
        helper.succeed();
    }

    private static void apiBalanceReadWrite(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ITheGoodsAPI api = ITheGoodsAPI.get();
        UUID player = UUID.randomUUID();
        long before = api.getBalance(server, player);
        helper.assertTrue(before == 0L, "new player starts at 0");

        Economy.give(server, UUID.randomUUID(), player, 1000L);
        long after = api.getBalance(server, player);
        helper.assertTrue(after == 1000L, "balance should be 1000 after give, got " + after);
        helper.succeed();
    }

    private static void apiItemEligibility(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ITheGoodsAPI api = ITheGoodsAPI.get();

        boolean eligible = api.isEligible(new ItemStack(Items.STICK));
        helper.assertTrue(eligible, "default stick stack should be eligible");
        helper.succeed();
    }

    private ApiGameTests() {
    }
}
