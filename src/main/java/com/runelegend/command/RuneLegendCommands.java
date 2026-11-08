package com.runelegend.command;

import net.minecraft.commands.arguments.IdentifierArgument;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.combat.StaminaManager;
import com.runelegend.player.PlayerTokenService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import com.runelegend.RuneLegendText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RuneLegendCommands {
    private RuneLegendCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            var root = literal("runelegend")
                    .then(literal("game")
                            .then(literal("status").executes(RuneLegendCommands::rules))
                            .then(literal("start").requires(PermissionPredicates.require(RuneLegendMod.id("game"), PermissionLevel.GAMEMASTERS))
                                    .executes(ctx -> changeRules(ctx, instance(ctx).game().startGame())))
                            .then(literal("stop").requires(PermissionPredicates.require(RuneLegendMod.id("game"), PermissionLevel.GAMEMASTERS))
                                    .executes(ctx -> changeRules(ctx, instance(ctx).game().stopGame()))));
            var token = literal("token")
                    .then(literal("list").executes(RuneLegendCommands::list))
                    .then(literal("info").then(tokenArg().executes(RuneLegendCommands::info)));
            addEquipmentChanges(token, ctx -> ctx.getSource().getPlayerOrException());
            addStatus(root, ctx -> ctx.getSource().getPlayerOrException());
            root.then(token);
            var admin = argument("player", EntityArgument.player());
            addEquipment(admin, ctx -> EntityArgument.getPlayer(ctx, "player"));
            root.then(literal("admin").requires(PermissionPredicates.require(RuneLegendMod.id("admin"), PermissionLevel.ADMINS)).then(admin));
            dispatcher.register(root);
        });
    }

    private interface Target { ServerPlayer get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException; }

    private static void addEquipment(ArgumentBuilder<CommandSourceStack, ?> parent, Target target) {
        addStatus(parent, target);
        addEquipmentChanges(parent, target);
    }

    private static void addStatus(ArgumentBuilder<CommandSourceStack, ?> parent, Target target) {
        parent.then(literal("status").executes(ctx -> status(ctx, target.get(ctx))));
    }

    private static void addEquipmentChanges(ArgumentBuilder<CommandSourceStack, ?> parent, Target target) {
        parent.then(literal("clear").executes(ctx -> {
            var player = target.get(ctx);
            return result(ctx, player, instance(ctx).players().clear(player.getUUID()));
        }));
        parent.then(literal("equip").then(tokenArg().executes(ctx -> changeEquipment(ctx, target.get(ctx), true))));
        parent.then(literal("unequip").then(tokenArg().executes(ctx -> changeEquipment(ctx, target.get(ctx), false))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Identifier> tokenArg() {
        return argument("token_id", IdentifierArgument.id()).suggests((ctx, builder) -> {
            var server = RuneLegendServer.find(ctx.getSource().getServer());
            if (server != null) {
                String prefix = builder.getRemaining();
                server.registry().all().forEach(definition -> {
                    if (definition.id().toString().startsWith(prefix)) builder.suggest(definition.id().toString());
                    else if (definition.id().getPath().startsWith(prefix)) builder.suggest(definition.id().getPath());
                });
            }
            return builder.buildFuture();
        });
    }

    private static Identifier tokenId(CommandContext<CommandSourceStack> ctx) {
        var id = ctx.getArgument("token_id", Identifier.class);
        return id.getNamespace().equals("minecraft") ? RuneLegendMod.id(id.getPath()) : id;
    }

    private static RuneLegendServer instance(CommandContext<CommandSourceStack> context) {
        var server = RuneLegendServer.find(context.getSource().getServer());
        if (server == null) throw new IllegalStateException("RuneLegend server has not started");
        return server;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var server = instance(ctx);
        server.registry().all().forEach(definition -> ctx.getSource().sendSuccess(
                () -> Component.literal(definition.id() + " — ").append(definition.name()), false));
        return server.registry().all().size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        var id = tokenId(ctx);
        var definition = id == null ? null : instance(ctx).registry().get(id);
        if (definition == null) {
            ctx.getSource().sendFailure(RuneLegendText.message("runelegend.result.unknown"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> definition.name().copy().append(": ").append(definition.description()), false);
        return 1;
    }

    private static int changeEquipment(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean equip) {
        var id = tokenId(ctx);
        if (id == null) return result(ctx, player, PlayerTokenService.Result.UNKNOWN);
        var service = instance(ctx).players();
        return result(ctx, player, equip ? service.equip(player.getUUID(), id) : service.unequip(player.getUUID(), id));
    }

    private static int result(CommandContext<CommandSourceStack> ctx, ServerPlayer player, PlayerTokenService.Result result) {
        var message = RuneLegendText.message("runelegend.result." + result.name().toLowerCase(java.util.Locale.ROOT), player.getDisplayName());
        if (result == PlayerTokenService.Result.CHANGED) {
            instance(ctx).sync().update(player);
            ctx.getSource().sendSuccess(() -> message, false);
            return 1;
        }
        if (result == PlayerTokenService.Result.UNCHANGED) ctx.getSource().sendSuccess(() -> message, false);
        else ctx.getSource().sendFailure(message);
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        var server = instance(ctx);
        int percent = server.stamina().stamina(player) * 100 / StaminaManager.MAX;
        var view = server.tokens().views(player);
        ctx.getSource().sendSuccess(() -> RuneLegendText.message("runelegend.status", player.getDisplayName(), percent), false);
        for (var token : view) {
            var definition = server.registry().get(token.id());
            var line = Component.literal("  " + token.id()).append(" — ");
            if (definition == null) line.append(RuneLegendText.message("runelegend.unavailable"));
            else {
                line.append(definition.name());
                if ((token.flags() & com.runelegend.token.TokenEngine.COOLDOWN) != 0) {
                    line.append(" (" + ((token.cooldownUntil() - server.tick() + 19) / 20) + "s)");
                }
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int rules(CommandContext<CommandSourceStack> ctx) {
        var rules = instance(ctx).game().getRules();
        ctx.getSource().sendSuccess(() -> RuneLegendText.message("runelegend.rules", rules.state().name()), false);
        return 1;
    }

    private static int changeRules(CommandContext<CommandSourceStack> ctx, boolean changed) {
        rules(ctx);
        return changed ? 1 : 0;
    }
}
