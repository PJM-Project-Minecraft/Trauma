package ru.liko.trauma.common.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.trauma.common.item.MedicalItem;
import ru.liko.trauma.common.item.MedicalItem.MedicalType;
import ru.liko.trauma.network.MedicalMinigameStartPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinigameManager {
    public static class Session {
        public int targetId;
        public MedicalType type;
        public long startTime;

        public Session(int targetId, MedicalType type) {
            this.targetId = targetId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, Session> activeSessions = new ConcurrentHashMap<>();

    public static void startSession(Player healer, int targetId, MedicalType type) {
        if (!healer.level().isClientSide() && healer instanceof ServerPlayer serverPlayer) {
            activeSessions.put(healer.getUUID(), new Session(targetId, type));
            PacketDistributor.sendToPlayer(serverPlayer, new MedicalMinigameStartPayload(targetId, type.ordinal()));
        }
    }

    public static void handleMinigameResult(Player healer, boolean success) {
        if (healer.level().isClientSide())
            return;

        Session session = activeSessions.remove(healer.getUUID());
        if (session != null) {
            if (System.currentTimeMillis() - session.startTime > 30000) {
                // Timeout, give them cooldown
                healer.getCooldowns().addCooldown(healer.getMainHandItem().getItem(),
                        ru.liko.trauma.Config.MEDICAL_COOLDOWN.get());
                return;
            }

            if (success) {
                Entity targetEntity = healer.level().getEntity(session.targetId);
                if (targetEntity instanceof net.minecraft.world.entity.LivingEntity patient) {
                    ItemStack heldItem = healer.getMainHandItem();
                    if (heldItem.getItem() instanceof MedicalItem medicalItem
                            && medicalItem.getMedicalType() == session.type) {
                        double maxDistance = ru.liko.trauma.Config.MEDICAL_MAX_DISTANCE.get();
                        if (healer.distanceTo(patient) <= maxDistance) {
                            if (medicalItem.applyTo(patient) && !healer.isCreative()) {
                                heldItem.shrink(1);
                            }
                            healer.getCooldowns().addCooldown(heldItem.getItem(),
                                    ru.liko.trauma.Config.MEDICAL_COOLDOWN.get());
                        } else {
                            healer.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.trauma.too_far"), true);
                        }
                    } else {
                        // They changed items or it's not a medical item anymore
                        healer.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.trauma.minigame_failed"),
                                true);
                    }
                }
            } else {
                healer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.trauma.minigame_failed"), true);
                healer.getCooldowns().addCooldown(healer.getMainHandItem().getItem(),
                        ru.liko.trauma.Config.MEDICAL_COOLDOWN.get());
            }
        }
    }
}
