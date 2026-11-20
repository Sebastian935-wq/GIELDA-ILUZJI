package pl.anarchia.gieldailuzji.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.event.FakeEventManager;
import pl.anarchia.gieldailuzji.item.FakeItemFactory;
import pl.anarchia.gieldailuzji.market.MarketManager;
import pl.anarchia.gieldailuzji.util.InventoryUtil;

public final class MarketCommand {
    private MarketCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String name : new String[]{"gieldailuzji", "fmarket"}) {
                dispatcher.register(build(name));
            }
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String name) {
        return CommandManager.literal(name)
                .executes(ctx -> {
                    MarketManager.open(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.literal("reload")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ModConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("Przeładowano gielda_iluzji.json"), true);
                            return 1;
                        }))
                .then(CommandManager.literal("event")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> startEvent(ctx.getSource(),
                                Math.random() < 0.5 ? FakeEventManager.Type.SUPPLY_DROP : FakeEventManager.Type.RAID))
                        .then(CommandManager.literal("drop")
                                .executes(ctx -> startEvent(ctx.getSource(), FakeEventManager.Type.SUPPLY_DROP)))
                        .then(CommandManager.literal("raid")
                                .executes(ctx -> startEvent(ctx.getSource(), FakeEventManager.Type.RAID))))
                .then(CommandManager.literal("lupa")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            InventoryUtil.give(p, FakeItemFactory.createLupa(ModConfig.get().lupaUses));
                            return 1;
                        }))
                .then(CommandManager.literal("szard")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("ile", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    InventoryUtil.giveShards(p, IntegerArgumentType.getInteger(ctx, "ile"));
                                    return 1;
                                })));
    }

    private static int startEvent(ServerCommandSource src, FakeEventManager.Type type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        boolean ok = FakeEventManager.get().start(p, type);
        if (!ok) src.sendError(Text.literal("Fałszywe eventy działają tylko w Overworldzie."));
        return ok ? 1 : 0;
    }
}
