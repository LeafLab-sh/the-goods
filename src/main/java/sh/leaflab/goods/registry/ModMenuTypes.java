package sh.leaflab.goods.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.TheGoods;
import sh.leaflab.goods.menu.DepositorMenu;
import sh.leaflab.goods.menu.TradeHubMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, TheGoods.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TradeHubMenu>> TRADE_HUB = MENU_TYPES.register(
            "trade_hub", () -> IMenuTypeExtension.create((windowId, inv, extraData) -> new TradeHubMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<DepositorMenu>> DEPOSITOR = MENU_TYPES.register(
            "depositor", () -> IMenuTypeExtension.create((windowId, inv, extraData) -> new DepositorMenu(windowId, inv, extraData.readUtf())));
}
