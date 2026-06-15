package ru.liko.trauma.common.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import ru.liko.trauma.Config;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class MedicalItem extends Item implements GeoItem {

    private static final String ALLY_HEAL_ANIM_TAG = "TraumaAllyHealAnim";

    public enum MedicalType {
        BANDAGE,
        TOURNIQUET
    }

    private final MedicalType type;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MedicalItem(Properties properties, MedicalType type) {
        super(properties);
        this.type = type;
    }

    public String getGeoId() {
        return switch (type) {
            case BANDAGE -> "bandage";
            case TOURNIQUET -> "tourniquet";
        };
    }

    public MedicalType getMedicalType() {
        return type;
    }

    public boolean canApplyTo(LivingEntity target) {
        if (!target.isAlive()) {
            return false;
        }
        return target.getHealth() + 1e-4f < target.getMaxHealth();
    }

    /**
     * Лечит ванильное HP. Вызывать только на сервере.
     *
     * @return true если было что лечить и применение состоялось
     */
    public boolean applyTo(LivingEntity target) {
        if (!canApplyTo(target)) {
            return false;
        }
        float amount = switch (type) {
            case BANDAGE -> Config.BANDAGE_VANILLA_HEALTH_HEAL.get().floatValue();
            case TOURNIQUET -> Config.TOURNIQUET_VANILLA_HEALTH_HEAL.get().floatValue();
        };
        if (amount <= 0f) {
            return false;
        }
        target.heal(amount);
        return true;
    }

    /**
     * Помечает стек перед startUsingItem на сервере после лечения другого игрока ЛКМ,
     * чтобы finishUsingItem не применил лечение к целителю и не расходовал предмет повторно.
     */
    public static void markAllyHealAnimation(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ALLY_HEAL_ANIM_TAG, true));
    }

    private static boolean isAllyHealAnimation(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ALLY_HEAL_ANIM_TAG);
    }

    private static void clearAllyHealAnimation(ItemStack stack) {
        if (isAllyHealAnimation(stack)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ALLY_HEAL_ANIM_TAG));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        String key = switch (type) {
            case BANDAGE -> "item.trauma.bandage.desc";
            case TOURNIQUET -> "item.trauma.tourniquet.desc";
        };
        tooltipComponents.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltipComponents
                .add(Component.translatable("item.trauma.medical.hint.self").withStyle(ChatFormatting.DARK_AQUA));
        tooltipComponents
                .add(Component.translatable("item.trauma.medical.hint.other").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
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
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return switch (type) {
            case BANDAGE -> 60;
            case TOURNIQUET -> 40;
        };
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!canApplyTo(player)) {
            return InteractionResultHolder.fail(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        clearAllyHealAnimation(stack);
        super.releaseUsing(stack, level, entity, timeCharged);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (isAllyHealAnimation(stack)) {
            clearAllyHealAnimation(stack);
            return stack;
        }
        if (!level.isClientSide()) {
            if (applyTo(entity)) {
                if (!(entity instanceof Player player) || !player.isCreative()) {
                    stack.shrink(1);
                }
                if (entity instanceof Player player) {
                    player.getCooldowns().addCooldown(this, Config.MEDICAL_COOLDOWN.get());
                }
            }
        }
        return stack;
    }
}
