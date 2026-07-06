package sh.leaflab.goods;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.block.NetworkConnectorBlock;
import sh.leaflab.goods.block.TradeHubBlock;
import sh.leaflab.goods.command.GoodsCommand;
import sh.leaflab.goods.datagen.DataGenerators;
import sh.leaflab.goods.gametest.EconomyGameTests;
import sh.leaflab.goods.network.NetworkHandler;
import sh.leaflab.goods.registry.ModBlockEntities;
import sh.leaflab.goods.registry.ModMenuTypes;

@Mod(TheGoods.MODID)
public class TheGoods {
    public static final String MODID = "thegoods";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<TradeHubBlock> TRADE_HUB = BLOCKS.registerBlock("trade_hub", TradeHubBlock::new, p -> p
            .mapColor(MapColor.WOOD)
            .destroyTime(2.0f)
            .explosionResistance(6.0f));
    public static final DeferredItem<BlockItem> TRADE_HUB_ITEM = ITEMS.registerSimpleBlockItem("trade_hub", TRADE_HUB);

    public static final DeferredBlock<NetworkConnectorBlock> NETWORK_CONNECTOR = BLOCKS.registerBlock("network_connector", NetworkConnectorBlock::new, p -> p
            .mapColor(MapColor.METAL)
            .destroyTime(1.5f)
            .explosionResistance(6.0f));
    public static final DeferredItem<BlockItem> NETWORK_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem("network_connector", NETWORK_CONNECTOR);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thegoods"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TRADE_HUB_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TRADE_HUB_ITEM.get());
                output.accept(NETWORK_CONNECTOR_ITEM.get());
            }).build());

    public TheGoods(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);

        EconomyGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(EconomyGameTests::register);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(GoodsCommand::register);

        modEventBus.addListener(DataGenerators::gatherClientData);
        modEventBus.addListener(DataGenerators::gatherServerData);

        modEventBus.addListener(NetworkHandler::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
