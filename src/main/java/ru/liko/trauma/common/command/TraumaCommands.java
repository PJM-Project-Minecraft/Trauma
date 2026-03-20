package ru.liko.trauma.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.effect.ModEffects;
import ru.liko.trauma.common.entity.MannequinEntity;
import ru.liko.trauma.common.event.SyncEventHandler;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.ragdoll.PhysicsWorld;
import ru.liko.trauma.ragdoll.PlayerRagdoll;
import ru.liko.trauma.ragdoll.RagdollPart;

@EventBusSubscriber(modid = Trauma.MODID)
public class TraumaCommands {

        public static boolean HITBOX_DEBUG_MODE = false;

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
                CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

                var healCmd = Commands.literal("heal")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> healPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
                        .executes(ctx -> healPlayer(ctx.getSource(), ctx.getSource().getPlayerOrException()));

                var damageCmd = Commands.literal("damage")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(1f, 5000f))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> damagePlayer(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                FloatArgumentType.getFloat(ctx, "amount"))))
                                .executes(ctx -> damagePlayer(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
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

                dispatcher.register(
                        Commands.literal("trauma")
                                .requires(source -> source.hasPermission(2))
                                .then(healCmd)
                                .then(damageCmd)
                                .then(debugCmd)
                                .then(ragdollCmd)
                );
        }

        private static int healPlayer(CommandSourceStack source, ServerPlayer target) {
                TraumaData data = target.getData(ModAttachments.TRAUMA_DATA);
                target.setData(ModAttachments.TRAUMA_DATA,
                                data.withBlood(TraumaData.MAX_BLOOD).withBleedStrength(0)
                                        .withLegFracture(0).withSplint(false).withLegDislocation(0));
                target.removeEffect(
                                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                                                .wrapAsHolder(ModEffects.BLEEDING.get()));

                // Remove max health penalty immediately so setHealth sets to full 20
                net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealthAttr = target
                                .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                        maxHealthAttr.removeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        Trauma.MODID, "blood_loss_health_penalty"));
                }

                target.setHealth(target.getMaxHealth());
                target.getFoodData().setFoodLevel(20);
                SyncEventHandler.syncData(target);

                source.sendSuccess(
                                () -> Component.translatable("commands.trauma.heal.success", target.getDisplayName()),
                                true);
                return 1;
        }

        private static int damagePlayer(CommandSourceStack source, ServerPlayer target, float amount) {
                TraumaData data = target.getData(ModAttachments.TRAUMA_DATA);
                target.setData(ModAttachments.TRAUMA_DATA, data.withBlood(data.bloodVolume() - amount));

                source.sendSuccess(
                                () -> Component.translatable("commands.trauma.damage.success", amount,
                                                target.getDisplayName()),
                                true);
                return 1;
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
