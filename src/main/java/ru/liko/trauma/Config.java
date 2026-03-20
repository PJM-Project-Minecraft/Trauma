package ru.liko.trauma;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Trauma.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        public enum BloodSystemMode {
                BLOOD_AND_HEALTH, // Health scales down with blood loss
                BLOOD_ONLY, // Death at 0 blood
                DISABLED // Vanilla behavior
        }

        public enum GuiPosition {
                BOTTOM_LEFT,
                BOTTOM_RIGHT,
                DISABLED
        }

        public static final ModConfigSpec.EnumValue<BloodSystemMode> BLOOD_SYSTEM_MODE;
        public static final ModConfigSpec.BooleanValue DEBUFFS_ENABLED;

        public static final ModConfigSpec.BooleanValue SHOW_VANILLA_HEALTH;
        public static final ModConfigSpec.EnumValue<GuiPosition> HUD_GUI_POSITION;
        public static final ModConfigSpec.EnumValue<GuiPosition> INVENTORY_GUI_POSITION;

        public static final ModConfigSpec.DoubleValue LIGHT_BLEED_DRAIN;
        public static final ModConfigSpec.DoubleValue HEAVY_BLEED_DRAIN;
        public static final ModConfigSpec.DoubleValue SEVERE_BLEED_DRAIN;

        public static final ModConfigSpec.DoubleValue MEDICAL_MAX_DISTANCE;
        public static final ModConfigSpec.IntValue MEDICAL_COOLDOWN;
        public static final ModConfigSpec.IntValue BANDAGE_HEAL_AMOUNT;

        public static final ModConfigSpec.DoubleValue FALL_DISTANCE_DISLOCATION;
        public static final ModConfigSpec.DoubleValue FALL_DISTANCE_FRACTURE;

        public static final ModConfigSpec.BooleanValue DEATH_RAGDOLL_ENABLED;
        public static final ModConfigSpec.IntValue DEATH_RAGDOLL_DURATION;
        public static final ModConfigSpec.BooleanValue DEATH_RAGDOLL_REACT_TO_FORCES;

        // Visual Effects
        public static final ModConfigSpec.BooleanValue ENABLE_HIT_BLUR;
        public static final ModConfigSpec.DoubleValue HIT_BLUR_MULTIPLIER;
        public static final ModConfigSpec.BooleanValue ENABLE_BLOOD_OVERLAY;

        static {
                BUILDER.push("Main System");
                BLOOD_SYSTEM_MODE = BUILDER
                                .comment(
                                                "BLOOD_AND_HEALTH: Health max value decreases with blood loss. BLOOD_ONLY: Instant death at 0 blood. DISABLED: Vanilla behavior.")
                                .defineEnum("bloodSystemMode", BloodSystemMode.BLOOD_AND_HEALTH);
                DEBUFFS_ENABLED = BUILDER
                                .comment("Enable negative potion effects (Slowness, Weakness, etc.) when blood is low.")
                                .define("debuffsEnabled", true);

                LIGHT_BLEED_DRAIN = BUILDER
                                .comment("Blood loss per second at bleed level 1.")
                                .defineInRange("lightBleedDrain", 1.0, 0.0, 100.0);
                HEAVY_BLEED_DRAIN = BUILDER
                                .comment("Blood loss per second at bleed level 2.")
                                .defineInRange("heavyBleedDrain", 5.0, 0.0, 100.0);
                SEVERE_BLEED_DRAIN = BUILDER
                                .comment("Blood loss per second at bleed level 3.")
                                .defineInRange("severeBleedDrain", 10.0, 0.0, 100.0);

                MEDICAL_MAX_DISTANCE = BUILDER
                                .comment("Maximum distance in blocks to heal another player with medical items.")
                                .defineInRange("medicalMaxDistance", 2.0, 0.5, 10.0);
                MEDICAL_COOLDOWN = BUILDER
                                .comment("Cooldown in ticks after using a medical item on another player (20 ticks = 1 second).")
                                .defineInRange("medicalCooldown", 20, 0, 600);
                BANDAGE_HEAL_AMOUNT = BUILDER
                                .comment("Number of bleed levels removed by a single bandage.")
                                .defineInRange("bandageHealAmount", 1, 1, 10);
                FALL_DISTANCE_DISLOCATION = BUILDER
                                .comment("Fall distance required to cause a leg dislocation.")
                                .defineInRange("fallDistanceDislocation", 4.0, 1.0, 100.0);
                FALL_DISTANCE_FRACTURE = BUILDER
                                .comment("Fall distance required to cause a leg fracture.")
                                .defineInRange("fallDistanceFracture", 6.0, 1.0, 100.0);
                BUILDER.pop();

                BUILDER.push("Ragdoll");
                DEATH_RAGDOLL_ENABLED = BUILDER
                                .comment("Enable death ragdoll — player body stays as ragdoll after death for a configurable duration.")
                                .define("deathRagdollEnabled", true);
                DEATH_RAGDOLL_DURATION = BUILDER
                                .comment("How long (in seconds) the death ragdoll persists before disappearing. 0 = instant removal.")
                                .defineInRange("deathRagdollDuration", 10, 0, 600);
                DEATH_RAGDOLL_REACT_TO_FORCES = BUILDER
                                .comment("Death ragdolls react to explosions, projectile impacts and other external forces.")
                                .define("deathRagdollReactToForces", true);
                BUILDER.pop();

                BUILDER.push("Visual Effects");
                ENABLE_HIT_BLUR = BUILDER
                                .comment("Enable blur effect when taking damage.")
                                .define("enableHitBlur", true);
                HIT_BLUR_MULTIPLIER = BUILDER
                                .comment("Multiplier for the hit blur intensity. Higher = stronger blur.")
                                .defineInRange("hitBlurMultiplier", 1.0, 0.0, 5.0);
                ENABLE_BLOOD_OVERLAY = BUILDER
                                .comment("Enable the red vignette blood overlay when low on blood.")
                                .define("enableBloodOverlay", true);
                BUILDER.pop();

                BUILDER.push("GUI and HUD");
                SHOW_VANILLA_HEALTH = BUILDER
                                .comment("Show the vanilla health bar (hearts) above the hotbar.")
                                .define("showVanillaHealth", false);
                HUD_GUI_POSITION = BUILDER
                                .comment("Position of the in-game HUD (outside inventory).")
                                .defineEnum("hudGuiPosition", GuiPosition.BOTTOM_LEFT);
                INVENTORY_GUI_POSITION = BUILDER
                                .comment("Position of the detailed GUI inside the inventory screen.")
                                .defineEnum("inventoryGuiPosition", GuiPosition.BOTTOM_RIGHT);
                BUILDER.pop();
        }

        static final ModConfigSpec SPEC = BUILDER.build();

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
        }
}
