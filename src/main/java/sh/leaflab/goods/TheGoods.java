package sh.leaflab.goods;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.block.TradeHubBlock;
import sh.leaflab.goods.command.GoodsCommand;
import sh.leaflab.goods.datagen.DataGenerators;
import sh.leaflab.goods.gametest.EconomyGameTests;
import sh.leaflab.goods.network.NetworkHandler;
import sh.leaflab.goods.registry.ModMenuTypes;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TheGoods.MODID)
public class TheGoods {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "thegoods";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "thegoods" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "thegoods" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "thegoods" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // The Trade Hub block: interact with it from any side to open the trade interface (see docs/spec.md).
    public static final DeferredBlock<TradeHubBlock> TRADE_HUB = BLOCKS.registerBlock("trade_hub", TradeHubBlock::new, p -> p
            .mapColor(MapColor.WOOD)
            .destroyTime(2.0f)
            .explosionResistance(6.0f));
    public static final DeferredItem<BlockItem> TRADE_HUB_ITEM = ITEMS.registerSimpleBlockItem("trade_hub", TRADE_HUB);

    // Creates a creative tab with the id "thegoods:tab" for the Trade Hub, placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thegoods")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TRADE_HUB_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TRADE_HUB_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public TheGoods(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register the Deferred Register to the mod event bus so menu types get registered
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // Register `./gradlew runGameTestServer` test functions and the tests that reference them
        EconomyGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(EconomyGameTests::register);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (TheGoods) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the /goods command tree
        NeoForge.EVENT_BUS.addListener(GoodsCommand::register);

        // Register the data generators (block/item models, recipes, loot tables) for `./gradlew runData`
        modEventBus.addListener(DataGenerators::gatherClientData);
        modEventBus.addListener(DataGenerators::gatherServerData);

        // Register networking payloads (e.g. balance sync while a Trade Hub menu is open)
        modEventBus.addListener(NetworkHandler::register);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
