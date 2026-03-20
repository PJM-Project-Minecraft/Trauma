package ru.liko.trauma.common.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.common.effect.ModEffects;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class MedicalItem extends Item implements GeoItem {
    public enum MedicalType {
        BANDAGE, TOURNIQUET, BLOOD_BAG, SPLINT
    }

    private final MedicalType type;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MedicalItem(Properties properties, MedicalType type) {
        super(properties);
        this.type = type;
    }

    /**
     * Returns an identifier for this item's geo resources (geo, texture).
     */
    public String getGeoId() {
        return switch (type) {
            case BANDAGE -> "bandage";
            case TOURNIQUET -> "tourniquet";
            case BLOOD_BAG -> "bloodbag";
            case SPLINT -> "splint";
        };
    }

    public MedicalType getMedicalType() {
        return type;
    }

    public boolean canApplyTo(LivingEntity target) {
        TraumaData data = target.getData(ModAttachments.TRAUMA_DATA);
        return switch (type) {
            case BLOOD_BAG -> data.bloodVolume() < TraumaData.MAX_BLOOD;
            case BANDAGE, TOURNIQUET -> data.bleedStrength() > 0;
            case SPLINT -> data.legFracture() > 0 && !data.hasSplint();
        };
    }

    public boolean applyTo(LivingEntity target) {
        TraumaData data = target.getData(ModAttachments.TRAUMA_DATA);
        Holder<MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING.get());

        switch (type) {
            case BANDAGE -> {
                int currentBleed = data.bleedStrength();
                if (currentBleed <= 0) {
                    return false;
                }

                int healAmount = ru.liko.trauma.Config.BANDAGE_HEAL_AMOUNT.get();
                currentBleed = Math.max(0, currentBleed - healAmount);
                target.setData(ModAttachments.TRAUMA_DATA, data.withBleedStrength(currentBleed));
                if (currentBleed == 0) {
                    target.removeEffect(bleedingHolder);
                } else {
                    target.addEffect(new MobEffectInstance(bleedingHolder, -1, currentBleed - 1, false, false, false));
                }
            }
            case TOURNIQUET -> {
                if (data.bleedStrength() <= 0) {
                    return false;
                }

                target.setData(ModAttachments.TRAUMA_DATA, data.withBleedStrength(0));
                target.removeEffect(bleedingHolder);
            }
            case BLOOD_BAG -> {
                if (data.bloodVolume() >= TraumaData.MAX_BLOOD) {
                    return false;
                }

                target.setData(ModAttachments.TRAUMA_DATA,
                        data.withBlood(Math.min(data.bloodVolume() + 1000f, TraumaData.MAX_BLOOD)));
            }
            case SPLINT -> {
                if (data.legFracture() <= 0 || data.hasSplint()) {
                    return false;
                }

                target.setData(ModAttachments.TRAUMA_DATA, data.withSplint(true));
            }
        }

        ru.liko.trauma.common.event.SyncEventHandler.syncData(target);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        String key = switch (type) {
            case BANDAGE -> "item.trauma.bandage.desc";
            case TOURNIQUET -> "item.trauma.tourniquet.desc";
            case BLOOD_BAG -> "item.trauma.blood_bag.desc";
            case SPLINT -> "item.trauma.splint.desc";
        };
        tooltipComponents.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltipComponents
                .add(Component.translatable("item.trauma.medical.hint.self").withStyle(ChatFormatting.DARK_AQUA));
        tooltipComponents
                .add(Component.translatable("item.trauma.medical.hint.other").withStyle(ChatFormatting.DARK_AQUA));
    }

    // ===== GeoItem Implementation =====

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        if (this.type == MedicalType.SPLINT) return; // Use vanilla 2D renderer for splint
        consumer.accept(new GeoRenderProvider() {
            private ru.liko.trauma.client.render.MedicalItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new ru.liko.trauma.client.render.MedicalItemRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Предметы не используют GeckoLib-анимации, только 3D-рендеринг модели.
        // Анимация использования — стандартная bow (UseAnim.BOW).
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===== Original MedicalItem Logic =====

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return switch (type) {
            case BANDAGE -> 60; // 3 seconds
            case TOURNIQUET -> 40; // 2 seconds
            case BLOOD_BAG -> 100; // 5 seconds
            case SPLINT -> 40; // 2 seconds
        };
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!canApplyTo(player)) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (this.type == MedicalType.SPLINT) {
            // Splint is applied instantly, no minigame
            if (!level.isClientSide()) {
                if (applyTo(player)) {
                    if (!player.isCreative()) {
                        itemstack.shrink(1);
                    }
                    player.getCooldowns().addCooldown(this, ru.liko.trauma.Config.MEDICAL_COOLDOWN.get());
                }
            }
            return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
        }

        // Trigger minigame on self ALWAYS for other items
        ru.liko.trauma.common.system.MinigameManager.startSession(player, player.getId(), type);
        return InteractionResultHolder.success(itemstack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving) {
        if (!level.isClientSide()) {
            if (applyTo(entityLiving)) {
                if (!(entityLiving instanceof Player player) || !player.isCreative()) {
                    stack.shrink(1);
                }
            }
        }
        return stack;
    }
}
