package sh.leaflab.goods.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import sh.leaflab.goods.economy.Currency;
import sh.leaflab.goods.economy.Economy;

public final class GoodsCommand {
    private static final DynamicCommandExceptionType INVALID_AMOUNT = new DynamicCommandExceptionType(
            input -> Component.translatable("commands.thegoods.invalid_amount", input));
    private static final SimpleCommandExceptionType AMOUNT_NOT_POSITIVE = new SimpleCommandExceptionType(
            Component.translatable("commands.thegoods.amount_not_positive"));

    private GoodsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("goods")
                .then(Commands.literal("balance")
                        .executes(GoodsCommand::balance))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(GoodsCommand::give))))
                .then(Commands.literal("take")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(GoodsCommand::take))))
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GoodsCommand::reset))));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long balance = Economy.getBalance(ctx.getSource().getServer(), player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.balance", Currency.format(balance)), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long amount = parseAmount(ctx, "amount");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Economy.give(ctx.getSource().getServer(), target.getUUID(), amount);
        long newBalance = Economy.getBalance(ctx.getSource().getServer(), target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.give", Currency.format(amount), target.getName(), Currency.format(newBalance)), true);
        return 1;
    }

    private static int take(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long amount = parseAmount(ctx, "amount");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Economy.take(ctx.getSource().getServer(), target.getUUID(), amount);
        long newBalance = Economy.getBalance(ctx.getSource().getServer(), target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.thegoods.take", Currency.format(amount), target.getName(), Currency.format(newBalance)), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Economy.reset(ctx.getSource().getServer(), target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.thegoods.reset", target.getName()), true);
        return 1;
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
}
