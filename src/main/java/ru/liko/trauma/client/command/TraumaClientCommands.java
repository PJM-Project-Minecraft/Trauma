package ru.liko.trauma.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;

/**
 * Client-only diagnostic / quick-toggle commands for HUD presentation.
 * <p>
 * These commands run in the client JVM (against the local {@link Config} static
 * fields) so they work on dedicated servers too — the server-side {@code /trauma}
 * commands cannot mutate client-side state across the network.
 * <p>
 * Usage in chat:
 * <ul>
 *   <li>{@code /traumahud info}      — show client-side HUD config + override</li>
 *   <li>{@code /traumahud minimal}   — force MINIMAL on this client</li>
 *   <li>{@code /traumahud auto}      — force AUTO on this client</li>
 *   <li>{@code /traumahud full}      — force FULL on this client</li>
 *   <li>{@code /traumahud reset}     — drop runtime override (use TOML value)</li>
 * </ul>
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public final class TraumaClientCommands {

    private TraumaClientCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("traumahud")
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> set(ctx.getSource(), null)))
                        .then(Commands.literal("minimal").executes(ctx -> set(ctx.getSource(), Config.HudPresentation.MINIMAL)))
                        .then(Commands.literal("auto").executes(ctx -> set(ctx.getSource(), Config.HudPresentation.AUTO)))
                        .then(Commands.literal("full").executes(ctx -> set(ctx.getSource(), Config.HudPresentation.FULL)))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .executes(ctx -> setByName(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset"))))
        );
    }

    private static int info(CommandSourceStack source) {
        Config.HudPresentation fromConfig = Config.HUD_PRESENTATION.get();
        Config.HudPresentation override = Config.getRuntimeHudOverride();
        Config.HudPresentation effective = Config.effectiveHudPresentation();
        Config.MedicalDifficulty diff = Config.MEDICAL_DIFFICULTY.get();
        Config.GuiPosition hudPos = Config.HUD_GUI_POSITION.get();

        String side = "CLIENT (local JVM @ " + Minecraft.getInstance().getUser().getName() + ")";

        source.sendSystemMessage(Component.literal("§6[Trauma HUD] §8" + side));
        source.sendSystemMessage(Component.literal("§7  HUD_PRESENTATION (TOML): §f" + fromConfig));
        source.sendSystemMessage(Component.literal("§7  Runtime override:       §f"
                + (override == null ? "<none>" : override.toString())));
        source.sendSystemMessage(Component.literal("§7  Effective:              §a" + effective));
        source.sendSystemMessage(Component.literal("§7  MEDICAL_DIFFICULTY:     §f" + diff));
        source.sendSystemMessage(Component.literal("§7  HUD_GUI_POSITION:       §f" + hudPos));
        source.sendSystemMessage(Component.literal(
                "§8 /traumahud minimal|auto|full|reset"));
        return 1;
    }

    private static int set(CommandSourceStack source, Config.HudPresentation preset) {
        Config.setRuntimeHudOverride(preset);
        String label = preset == null ? "<reset to TOML>" : preset.toString();
        source.sendSystemMessage(Component.literal(
                "§a[Trauma HUD] client override = §f" + label));
        return 1;
    }

    private static int setByName(CommandSourceStack source, String preset) {
        if (preset.equalsIgnoreCase("reset") || preset.equalsIgnoreCase("none")) {
            return set(source, null);
        }
        Config.HudPresentation chosen;
        try {
            chosen = Config.HudPresentation.valueOf(preset.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal(
                    "§c[Trauma HUD] unknown preset '" + preset + "'. Use: minimal | auto | full | reset"));
            return 0;
        }
        return set(source, chosen);
    }
}
