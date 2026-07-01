package sh.leaflab.goods.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import sh.leaflab.goods.menu.TradeHubMenu;

public class NetworkHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC, NetworkHandler::handleBalanceSync);
    }

    private static void handleBalanceSync(BalanceSyncPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TradeHubMenu menu) {
            menu.setClientBalance(payload.balance());
        }
    }
}
