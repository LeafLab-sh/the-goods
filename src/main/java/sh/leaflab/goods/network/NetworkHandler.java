package sh.leaflab.goods.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import sh.leaflab.goods.menu.NetworkConnectorMenu;
import sh.leaflab.goods.menu.TradeHubMenu;

public class NetworkHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC, NetworkHandler::handleBalanceSync);
        registrar.playToServer(CatalogQueryPayload.TYPE, CatalogQueryPayload.STREAM_CODEC, NetworkHandler::handleCatalogQuery);
        registrar.playToClient(CatalogResultPayload.TYPE, CatalogResultPayload.STREAM_CODEC, NetworkHandler::handleCatalogResult);
        registrar.playToServer(BuyRequestPayload.TYPE, BuyRequestPayload.STREAM_CODEC, NetworkHandler::handleBuyRequest);
        registrar.playToClient(BuyResultPayload.TYPE, BuyResultPayload.STREAM_CODEC, NetworkHandler::handleBuyResult);
        registrar.playToServer(SetSellDialogModePayload.TYPE, SetSellDialogModePayload.STREAM_CODEC, NetworkHandler::handleSetSellDialogMode);
        registrar.playToServer(SellDecisionPayload.TYPE, SellDecisionPayload.STREAM_CODEC, NetworkHandler::handleSellDecision);
        registrar.playToClient(SellPreviewPayload.TYPE, SellPreviewPayload.STREAM_CODEC, NetworkHandler::handleSellPreview);
        registrar.playToServer(SetNetworkNamePayload.TYPE, SetNetworkNamePayload.STREAM_CODEC, NetworkHandler::handleSetNetworkName);
    }

    private static void handleBalanceSync(BalanceSyncPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TradeHubMenu menu) {
            menu.setClientBalance(payload.balance());
        }
    }

    private static void handleCatalogQuery(CatalogQueryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof TradeHubMenu menu) {
            menu.handleCatalogQuery(payload);
        }
    }

    private static void handleCatalogResult(CatalogResultPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TradeHubMenu menu) {
            menu.setClientCatalogResult(payload);
        }
    }

    private static void handleBuyRequest(BuyRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof TradeHubMenu menu) {
            menu.handleBuyRequest(payload);
        }
    }

    private static void handleBuyResult(BuyResultPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TradeHubMenu menu) {
            menu.setClientLastBuyResult(payload);
        }
    }

    private static void handleSetSellDialogMode(SetSellDialogModePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof TradeHubMenu menu) {
            menu.handleSellDialogModeChange(payload.enabled());
        }
    }

    private static void handleSellDecision(SellDecisionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof TradeHubMenu menu) {
            menu.handleSellDecision(payload);
        }
    }

    private static void handleSellPreview(SellPreviewPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TradeHubMenu menu) {
            menu.setClientSellPreviewStock(payload.stockBeforeSale());
        }
    }

    private static void handleSetNetworkName(SetNetworkNamePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof NetworkConnectorMenu menu) {
            menu.setNetworkName(serverPlayer, payload.name());
        }
    }
}
