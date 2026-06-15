package ru.liko.trauma.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.entity.MannequinEntity;
import ru.liko.trauma.ragdoll.PhysicsWorld;
import ru.liko.trauma.ragdoll.PlayerRagdoll;
import ru.liko.trauma.ragdoll.RagdollPart;

@EventBusSubscriber(modid = Trauma.MODID)
public class TraumaCommands {

        public static boolean HITBOX_DEBUG_MODE = false;

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
                CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

                // Use EntityArgument.players() so selectors like @a / @e[type=player] / @r work.
                // It still accepts single player names and UUIDs, so /trauma heal Steve keeps working.
                var healCmd = Commands.literal("heal")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> healPlayers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))
                        .executes(ctx -> healPlayers(ctx.getSource(),
                                java.util.List.of(ctx.getSource().getPlayerOrException())));

                var damageCmd = Commands.literal("damage")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(1f, 5000f))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> damagePlayers(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                FloatArgumentType.getFloat(ctx, "amount"))))
                                .executes(ctx -> damagePlayers(ctx.getSource(),
                                        java.util.List.of(ctx.getSource().getPlayerOrException()),
                                        FloatArgumentType.getFloat(ctx, "amount"))));

                var debugCmd = Commands.literal("debug")
                        .then(Commands.literal("hitboxes")
                                .executes(ctx -> toggleDebug(ctx.getSource())));

                var ragdollToggle = Commands.literal("toggle")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> toggleRagdoll(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
                        .executes(ctx -> toggleRagdoll(ctx.getSource(), ctx.getSource().getPlayerOrException()));

                var ragdollMannequin = Commands.literal("mannequin")
                        .executes(ctx -> spawnMannequin(ctx.getSource()));

                var ragdollImpulse = Commands.literal("impulse")
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.1f, 100f))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> applyRagdollImpulse(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                FloatArgumentType.getFloat(ctx, "strength"))))
                                .executes(ctx -> applyRagdollImpulse(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
                                        FloatArgumentType.getFloat(ctx, "strength"))));

                var ragdollCmd = Commands.literal("ragdoll")
                        .then(ragdollToggle)
                        .then(ragdollMannequin)
                        .then(ragdollImpulse);

                var hudInfo = Commands.literal("hud-info")
                        .executes(ctx -> printHudInfo(ctx.getSource()));

                var hudSet = Commands.literal("hud-set")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .executes(ctx -> setHudPreset(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset"))));

                dispatcher.register(
                        Commands.literal("trauma")
                                .requires(source -> source.hasPermission(2))
                                .then(healCmd)
                                .then(damageCmd)
                                .then(debugCmd)
                                .then(ragdollCmd)
                                .then(hudInfo)
                                .then(hudSet)
                );
        }

        private static int printHudInfo(CommandSourceStack source) {
                Config.HudPresentation fromConfig = Config.HUD_PRESENTATION.get();
                Config.HudPresentation override = Config.getRuntimeHudOverride();
                Config.HudPresentation effective = Config.effectiveHudPresentation();
                Config.MedicalDifficulty diff = Config.MEDICAL_DIFFICULTY.get();
                Config.GuiPosition hudPos = Config.HUD_GUI_POSITION.get();

                source.sendSystemMessage(Component.literal("§6[Trauma HUD diagnostics]"));
                source.sendSystemMessage(Component.literal("§7  HUD_PRESENTATION (TOML): §f" + fromConfig));
                source.sendSystemMessage(Component.literal("§7  Runtime override:       §f"
                                + (override == null ? "<none>" : override.toString())));
                source.sendSystemMessage(Component.literal("§7  Effective:              §a" + effective));
                source.sendSystemMessage(Component.literal("§7  MEDICAL_DIFFICULTY:     §f" + diff));
                source.sendSystemMessage(Component.literal("§7  HUD_GUI_POSITION:       §f" + hudPos));
                source.sendSystemMessage(Component.literal(
                                "§8 Use §f/trauma hud-set <auto|minimal|full>§8 to override at runtime."));
                return 1;
        }

        private static int setHudPreset(CommandSourceStack source, String preset) {
                Config.HudPresentation chosen;
                try {
                        chosen = Config.HudPresentation.valueOf(preset.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                        source.sendFailure(Component.literal(
                                        "§cUnknown preset '" + preset + "'. Valid: auto | minimal | full"));
                        return 0;
                }
                Config.setRuntimeHudOverride(chosen);
                source.sendSuccess(() -> Component.literal(
                                "§aHUD preset set to §f" + chosen + "§a (runtime override, not saved to config)."), true);
                return 1;
        }

        private static int healPlayers(CommandSourceStack source, java.util.Collection<ServerPlayer> targets) {
                int count = 0;
                for (ServerPlayer target : targets) {
                        healSingle(target);
                        count++;
                }

                final int healed = count;
                if (healed == 1) {
                        ServerPlayer only = targets.iterator().next();
                        source.sendSuccess(
                                        () -> Component.translatable("commands.trauma.heal.success", only.getDisplayName()),
                                        true);
                } else {
                        source.sendSuccess(() -> Component.literal(
                                        "§a[Trauma] healed §f" + healed + "§a player(s)"), true);
                }
                return healed;
        }

        private static void healSingle(ServerPlayer target) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealthAttr = target
                                .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                        maxHealthAttr.removeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        Trauma.MODID, "blood_loss_health_penalty"));
                }

                target.setHealth(target.getMaxHealth());
                target.getFoodData().setFoodLevel(20);
        }

        private static int damagePlayers(CommandSourceStack source, java.util.Collection<ServerPlayer> targets,
                        float amount) {
                int count = 0;
                float dmg = Math.min(amount, 500f);
                for (ServerPlayer target : targets) {
                        target.hurt(target.damageSources().magic(), dmg);
                        count++;
                }

                final int hit = count;
                if (hit == 1) {
                        ServerPlayer only = targets.iterator().next();
                        source.sendSuccess(
                                        () -> Component.translatable("commands.trauma.damage.success", dmg,
                                                        only.getDisplayName()),
                                        true);
                } else {
                        source.sendSuccess(() -> Component.literal(
                                        "§e[Trauma] applied §f" + dmg + "§e magic damage to §f" + hit + "§e player(s)"),
                                        true);
                }
                return hit;
        }

        private static int toggleDebug(CommandSourceStack source) {
                HITBOX_DEBUG_MODE = !HITBOX_DEBUG_MODE;
                source.sendSuccess(() -> Component.literal("Trauma Hitbox Debug Mode: " + HITBOX_DEBUG_MODE), true);
                return 1;
        }

        // ======================== RAGDOLL COMMANDS ========================

        private static int toggleRagdoll(CommandSourceStack source, ServerPlayer target) {
                ServerLevel level = target.serverLevel();
                PhysicsWorld physicsWorld = PhysicsWorld.get(level);
                PlayerRagdoll ragdoll = physicsWorld.getPlayerRagdoll(target);

                if (ragdoll == null) {
                        physicsWorld.addPlayer(target);
                        ragdoll = physicsWorld.getPlayerRagdoll(target);
                }

                if (ragdoll == null) {
                        source.sendFailure(Component.literal("Failed to create ragdoll for " + target.getName().getString()));
                        return 0;
                }

                if (ragdoll.getMode() == PlayerRagdoll.Mode.RAGDOLL) {
                        ragdoll.setMode(PlayerRagdoll.Mode.NORMAL);
                        source.sendSuccess(() -> Component.literal("Ragdoll OFF for " + target.getName().getString()), true);
                } else {
                        ragdoll.setMode(PlayerRagdoll.Mode.RAGDOLL);
                        source.sendSuccess(() -> Component.literal("Ragdoll ON for " + target.getName().getString()), true);
                }
                return 1;
        }

        private static int spawnMannequin(CommandSourceStack source) {
                if (!(source.getLevel() instanceof ServerLevel level)) {
                        source.sendFailure(Component.literal("Must be run in a world"));
                        return 0;
                }

                ServerPlayer player = source.getPlayer();
                Vec3 pos;
                float yRot = 0;
                if (player != null) {
                        // Spawn at player's look position (3 blocks ahead)
                        Vec3 look = player.getLookAngle().scale(3.0);
                        pos = player.getEyePosition().add(look);
                        yRot = player.getYRot();
                } else {
                        pos = source.getPosition();
                }

                MannequinEntity mannequin = new MannequinEntity(
                        ru.liko.trauma.bloodybits.registry.ModEntityTypes.MANNEQUIN.get(), level);
                mannequin.moveTo(pos.x, pos.y, pos.z, yRot, 0f);
                level.addFreshEntity(mannequin);

                source.sendSuccess(() -> Component.literal("Spawned mannequin at " +
                        String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z) +
                        " — Right-click to toggle ragdoll, Sneak+click to remove"), true);
                return 1;
        }

        private static int applyRagdollImpulse(CommandSourceStack source, ServerPlayer target, float strength) {
                ServerLevel level = target.serverLevel();
                PhysicsWorld physicsWorld = PhysicsWorld.get(level);
                PlayerRagdoll ragdoll = physicsWorld.getPlayerRagdoll(target);

                if (ragdoll == null || ragdoll.getMode() != PlayerRagdoll.Mode.RAGDOLL) {
                        source.sendFailure(Component.literal(target.getName().getString() +
                                " is not in ragdoll mode. Use /trauma ragdoll toggle first."));
                        return 0;
                }

                // Apply upward + random impulse
                ragdoll.applyRandomVelocity(strength * 2f, strength, strength * 2f);
                source.sendSuccess(() -> Component.literal("Applied impulse (strength=" + strength +
                        ") to " + target.getName().getString()), true);
                return 1;
        }
}
