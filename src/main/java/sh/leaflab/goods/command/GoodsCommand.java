package sh.leaflab.goods.command;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.economy.Economy;
import sh.leaflab.goods.economy.TradeRequest;

public final class GoodsCommand {
    private static final DynamicCommandExceptionType INVALID_AMOUNT = new DynamicCommandExceptionType(
            input -> Component.translatable("commands.thegoods.invalid_amount", input));
    private static final SimpleCommandExceptionType AMOUNT_NOT_POSITIVE = new SimpleCommandExceptionType(
            Component.translatable("commands.thegoods.amount_not_positive"));
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(
            input -> Component.translatable("commands.thegoods.unknown_player", input));
    private static final SimpleCommandExceptionType CANNOT_TARGET_SELF = new SimpleCommandExceptionType(
            Component.translatable("commands.thegoods.cannot_target_self"));
    private static final SimpleCommandExceptionType INSUFFICIENT_BALANCE = new SimpleCommandExceptionType(
            Component.translatable("commands.thegoods.insufficient_balance"));
    private static final DynamicCommandExceptionType NO_PENDING_REQUEST = new DynamicCommandExceptionType(
            input -> Component.translatable("commands.thegoods.no_pending_request", input));

    private GoodsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("goods")
                .then(Commands.literal("balance")
                        .executes(GoodsCommand::balance))
                .then(Commands.literal("pay")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::pay))))
                .then(Commands.literal("request")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::request)))
                        .then(Commands.literal("accept")
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::requestAccept)))
                        .then(Commands.literal("deny")
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::requestDeny)))
                        .then(Commands.literal("cancel")
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::requestCancel)))
                        .then(Commands.literal("list")
                                .executes(GoodsCommand::requestList)))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::give))))
                .then(Commands.literal("take")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(playerArgument("player")
                                        .executes(GoodsCommand::take))))
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(playerArgument("player")
                                .executes(GoodsCommand::reset))));
    }

    // StringArgumentType (not EntityArgument.player()) is used for player names so offline players can be
    // targeted too (see resolvePlayer/nameToIdCache below) — but that means Brigadier no longer suggests names
    // for free, so we wire the same online-player suggestion list back in by hand.
    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long balance = Economy.getBalance(ctx.getSource().getServer(), player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.balance", Currency.format(balance)), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long amount = parseAmount(ctx, "amount");
        NameAndId target = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        Economy.give(server, target.id(), amount);
        long newBalance = Economy.getBalance(server, target.id());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.give", Currency.format(amount), target.name(), Currency.format(newBalance)), true);
        notifyIfOnline(server, target.id(), () -> Component.translatable(
                "commands.thegoods.give.notify", Currency.format(amount), Currency.format(newBalance)));
        return 1;
    }

    private static int take(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long amount = parseAmount(ctx, "amount");
        NameAndId target = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        Economy.take(server, target.id(), amount);
        long newBalance = Economy.getBalance(server, target.id());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.take", Currency.format(amount), target.name(), Currency.format(newBalance)), true);
        notifyIfOnline(server, target.id(), () -> Component.translatable(
                "commands.thegoods.take.notify", Currency.format(amount), Currency.format(newBalance)));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NameAndId target = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        Economy.reset(server, target.id());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.reset", target.name()), true);
        notifyIfOnline(server, target.id(), () -> Component.translatable("commands.thegoods.reset.notify"));
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        long amount = parseAmount(ctx, "amount");
        NameAndId target = resolveOtherPlayer(ctx, sender.getUUID(), "player");
        MinecraftServer server = ctx.getSource().getServer();

        if (Economy.getBalance(server, sender.getUUID()) < amount) {
            throw INSUFFICIENT_BALANCE.create();
        }
        Economy.transfer(server, sender.getUUID(), target.id(), amount);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.pay", Currency.format(amount), target.name()), true);
        notifyIfOnline(server, target.id(), () -> Component.translatable(
                "commands.thegoods.pay.notify", sender.getName(), Currency.format(amount)));
        return 1;
    }

    // "request 50 Bob" — the executor (requester) is asking to receive 50 from Bob (the payer), pending Bob's accept.
    private static int request(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        long amount = parseAmount(ctx, "amount");
        NameAndId payer = resolveOtherPlayer(ctx, requester.getUUID(), "player");
        MinecraftServer server = ctx.getSource().getServer();

        Economy.putRequest(server, new TradeRequest(requester.getUUID(), payer.id(), amount));
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.request.sent", Currency.format(amount), payer.name()), false);

        String requesterName = requester.getGameProfile().name();
        notifyIfOnline(server, payer.id(), () -> Component.translatable(
                        "commands.thegoods.request.notify", requester.getName(), Currency.format(amount))
                .append(" ").append(acceptButton(requesterName)).append(" ").append(denyButton(requesterName)));
        return 1;
    }

    private static int requestAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer payer = ctx.getSource().getPlayerOrException();
        NameAndId requester = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        TradeRequest pending = Economy.findRequest(server, requester.id(), payer.getUUID())
                .orElseThrow(() -> NO_PENDING_REQUEST.create(requester.name()));

        // Re-validate at resolution time — the payer's balance may have dropped since the request was made.
        if (Economy.getBalance(server, payer.getUUID()) < pending.amount()) {
            throw INSUFFICIENT_BALANCE.create();
        }
        Economy.removeRequest(server, requester.id(), payer.getUUID());
        Economy.transfer(server, payer.getUUID(), requester.id(), pending.amount());

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.request.accepted", Currency.format(pending.amount()), requester.name()), true);
        notifyIfOnline(server, requester.id(), () -> Component.translatable(
                "commands.thegoods.request.accepted.notify", payer.getGameProfile().name(), Currency.format(pending.amount())));
        return 1;
    }

    private static int requestDeny(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer payer = ctx.getSource().getPlayerOrException();
        NameAndId requester = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        if (!Economy.removeRequest(server, requester.id(), payer.getUUID())) {
            throw NO_PENDING_REQUEST.create(requester.name());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.request.denied", requester.name()), false);
        notifyIfOnline(server, requester.id(), () -> Component.translatable(
                "commands.thegoods.request.denied.notify", payer.getGameProfile().name()));
        return 1;
    }

    private static int requestCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        NameAndId payer = resolvePlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        boolean removed = Economy.removeRequest(server, requester.getUUID(), payer.id());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                removed ? "commands.thegoods.request.cancelled" : "commands.thegoods.request.nothing_to_cancel", payer.name()), false);
        if (removed) {
            notifyIfOnline(server, payer.id(), () -> Component.translatable(
                    "commands.thegoods.request.cancelled.notify", requester.getGameProfile().name()));
        }
        return 1;
    }

    private static int requestList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        UUID uuid = player.getUUID();

        List<TradeRequest> incoming = Economy.incomingRequests(server, uuid);
        List<TradeRequest> outgoing = Economy.outgoingRequests(server, uuid);

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.request.list.empty"), false);
            return 1;
        }

        for (TradeRequest request : incoming) {
            String name = playerName(server, request.requester());
            ctx.getSource().sendSuccess(() -> Component.translatable(
                            "commands.thegoods.request.list.incoming", name, Currency.format(request.amount()))
                    .append(" ").append(acceptButton(name)).append(" ").append(denyButton(name)), false);
        }
        for (TradeRequest request : outgoing) {
            String name = playerName(server, request.payer());
            ctx.getSource().sendSuccess(() -> Component.translatable(
                            "commands.thegoods.request.list.outgoing", Currency.format(request.amount()), name)
                    .append(" ").append(cancelButton(name)), false);
        }
        return 1;
    }

    private static MutableComponent acceptButton(String counterpartName) {
        return Component.translatable("commands.thegoods.request.accept_button")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/goods request accept " + counterpartName))
                        .withColor(ChatFormatting.GREEN));
    }

    private static MutableComponent denyButton(String counterpartName) {
        return Component.translatable("commands.thegoods.request.deny_button")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/goods request deny " + counterpartName))
                        .withColor(ChatFormatting.RED));
    }

    private static MutableComponent cancelButton(String counterpartName) {
        return Component.translatable("commands.thegoods.request.cancel_button")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/goods request cancel " + counterpartName))
                        .withColor(ChatFormatting.RED));
    }

    private static long parseAmount(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, argName);
        long amount;
        try {
            amount = Currency.parseExact(raw);
        } catch (NumberFormatException | ArithmeticException e) {
            throw INVALID_AMOUNT.create(raw);
        }
        if (amount <= 0) {
            throw AMOUNT_NOT_POSITIVE.create();
        }
        return amount;
    }

    private static NameAndId resolvePlayer(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, argName);
        return ctx.getSource().getServer().services().nameToIdCache().get(name)
                .orElseThrow(() -> UNKNOWN_PLAYER.create(name));
    }

    private static NameAndId resolveOtherPlayer(CommandContext<CommandSourceStack> ctx, UUID self, String argName) throws CommandSyntaxException {
        NameAndId resolved = resolvePlayer(ctx, argName);
        if (resolved.id().equals(self)) {
            throw CANNOT_TARGET_SELF.create();
        }
        return resolved;
    }

    private static String playerName(MinecraftServer server, UUID uuid) {
        return server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse(uuid.toString());
    }

    private static void notifyIfOnline(MinecraftServer server, UUID playerId, java.util.function.Supplier<Component> message) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            online.sendSystemMessage(message.get());
        }
    }
}
