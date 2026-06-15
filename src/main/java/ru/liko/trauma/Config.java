package ru.liko.trauma;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Trauma.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
        private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

        public enum BloodSystemMode {
                BLOOD_AND_HEALTH, // Health scales down with blood loss (Squad style)
                BLOOD_ONLY,       // Death at 0 blood
                DISABLED          // Vanilla behavior
        }

        public enum MedicalDifficulty {
                SQUAD,      // Fast-paced, forgiving, squad-focused
                REFORGER,   // Realistic Arma-style medical with proper sequencing
                HARDCORE    // Brutal, slow recovery, high lethality
        }

        public enum GuiPosition {
                BOTTOM_LEFT,
                BOTTOM_RIGHT,
                DISABLED
        }

        /**
         * Только отрисовка HUD. Не путать с {@link MedicalDifficulty}: можно играть с механикой
         * Reforger, но с минимальным интерфейсом Squad.
         */
        public enum HudPresentation {
                /** Минимальный HUD только если {@link MedicalDifficulty} = SQUAD */
                AUTO,
                /** Всегда стиль Squad: без полос HP/крови/травмы на экране */
                MINIMAL,
                /** Всегда полный тактический HUD с полосами */
                FULL
        }

        public static final ModConfigSpec.EnumValue<BloodSystemMode> BLOOD_SYSTEM_MODE;
        public static final ModConfigSpec.EnumValue<MedicalDifficulty> MEDICAL_DIFFICULTY;

        public static final ModConfigSpec.BooleanValue DEBUFFS_ENABLED;
        public static final ModConfigSpec.BooleanValue BLEEDING_ENABLED;
        public static final ModConfigSpec.BooleanValue TRAUMA_SYSTEM_ENABLED;
        public static final ModConfigSpec.BooleanValue PAIN_SYSTEM_ENABLED;
        public static final ModConfigSpec.BooleanValue CARDIAC_ARREST_ENABLED;

        public static final ModConfigSpec.BooleanValue SHOW_VANILLA_HEALTH;
        public static final ModConfigSpec.EnumValue<HudPresentation> HUD_PRESENTATION;
        public static final ModConfigSpec.EnumValue<GuiPosition> HUD_GUI_POSITION;
        public static final ModConfigSpec.EnumValue<GuiPosition> INVENTORY_GUI_POSITION;

        // Bleeding & Wound System (core of hardcore medical)
        public static final ModConfigSpec.DoubleValue BASE_BLEED_RATE;
        public static final ModConfigSpec.DoubleValue HEAVY_BLEED_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue WOUND_SEVERITY_TO_BLEED;

        public static final ModConfigSpec.DoubleValue TRAUMA_PER_DAMAGE;
        public static final ModConfigSpec.DoubleValue TRAUMA_DECAY_RATE;
        public static final ModConfigSpec.DoubleValue PAIN_DECAY_RATE;

        public static final ModConfigSpec.DoubleValue BLOOD_REGEN_RATE;
        public static final ModConfigSpec.DoubleValue MORPHINE_DURATION_SECONDS;
        public static final ModConfigSpec.DoubleValue BANDAGE_HEAL_AMOUNT;
        public static final ModConfigSpec.BooleanValue BANDAGE_HEALS_VANILLA_HEALTH;
        public static final ModConfigSpec.DoubleValue BANDAGE_VANILLA_HEALTH_HEAL;
        public static final ModConfigSpec.DoubleValue TOURNIQUET_VANILLA_HEALTH_HEAL;

        public static final ModConfigSpec.DoubleValue MEDICAL_MAX_DISTANCE;
        public static final ModConfigSpec.IntValue MEDICAL_COOLDOWN;

        // Hardcore thresholds
        public static final ModConfigSpec.DoubleValue CARDIAC_ARREST_BLOOD_THRESHOLD;
        public static final ModConfigSpec.DoubleValue UNCONSCIOUS_TRAUMA_THRESHOLD;
        public static final ModConfigSpec.DoubleValue CRITICAL_TRAUMA_DEBUFF;

        public static final ModConfigSpec.BooleanValue DEATH_RAGDOLL_ENABLED;
        public static final ModConfigSpec.IntValue DEATH_RAGDOLL_DURATION;
        public static final ModConfigSpec.BooleanValue DEATH_RAGDOLL_REACT_TO_FORCES;

        /** Death corpse (physics body) HP — depleted by damage/explosion; corpse dissolves early at 0. */
        public static final ModConfigSpec.DoubleValue DEATH_CORPSE_INTEGRITY;
        /** Global scale for impulse from explosions on ragdolls (lower = less “rocket” flight). */
        public static final ModConfigSpec.DoubleValue RAGDOLL_EXPLOSION_IMPULSE_SCALE;
        /** Hard cap on central impulse magnitude from one explosion impulse pass (per limb). */
        public static final ModConfigSpec.DoubleValue RAGDOLL_EXPLOSION_MAX_IMPULSE;
        /** Max linear velocity for living ragdoll mode (bullet units). */
        public static final ModConfigSpec.DoubleValue RAGDOLL_MAX_LINEAR_SPEED;
        /** Max linear velocity while in death corpse mode (much lower feels heavier). */
        public static final ModConfigSpec.DoubleValue RAGDOLL_DEATH_CORPSE_MAX_LINEAR_SPEED;
        /** Max strength passed to knockback impulses from damage/heavy hits. */
        public static final ModConfigSpec.DoubleValue RAGDOLL_KNOCKBACK_STRENGTH_CAP;
        /** Multiplier applied to corpse integrity loss vs recorded damage ticks (dead entity). */
        public static final ModConfigSpec.DoubleValue RAGDOLL_CORPSE_DAMAGE_FACTOR;
        /**
         * Orphan corpse has no Minecraft entity receiving explosion hurtEvents — approximate damage
         * to corpse integrity using blast exposure (fraction of nominal damage).
         */
        public static final ModConfigSpec.DoubleValue RAGDOLL_ORPHAN_EXPLOSION_CORPSE_DAMAGE;
        /** Dripping blood particles along death ragdolls (requires BloodyBits blood spray entities). */
        public static final ModConfigSpec.BooleanValue RAGDOLL_DEATH_CORPSE_BLOOD_DRIP;
        /** Ticks between slow blood drips spawned at a random limb. */
        public static final ModConfigSpec.IntValue RAGDOLL_DEATH_CORPSE_BLOOD_DRIP_INTERVAL;

        // Visual & Squad mechanics
        public static final ModConfigSpec.BooleanValue ENABLE_HIT_BLUR;
        public static final ModConfigSpec.BooleanValue HIT_BLUR_USES_POST_PROCESS;
        public static final ModConfigSpec.DoubleValue HIT_BLUR_MULTIPLIER;
        public static final ModConfigSpec.BooleanValue ENABLE_BLOOD_OVERLAY;
        public static final ModConfigSpec.BooleanValue LOW_HEALTH_USES_POST_PROCESS;
        public static final ModConfigSpec.DoubleValue SUPPRESSION_STRENGTH;

        static {
                // --- Две независимые оси настроек (не конфликтуют): ---
                // bloodSystemMode = как объём крови влияет на ванильное HP и смерть.
                // medicalDifficulty = стиль интерфейса (Squad vs полные полосы) + суровость лечения/урона.

                BUILDER.push("Blood Volume & Vanilla Health");
                BLOOD_SYSTEM_MODE = BUILDER
                                .comment("How blood ties to Minecraft health. Independent of 'medicalDifficulty' (which is HUD + difficulty preset).",
                                                "BLOOD_AND_HEALTH: max health drops as blood is lost.",
                                                "BLOOD_ONLY: instant death at 0 blood; max health unchanged by blood.",
                                                "DISABLED: completely off — no bleeding, blood loss, blood bars/panel lines, or blood debuffs; data is reset to full blood / no bleed on the server. BloodyBits splatter particles still spawn on damage (visual only).")
                                .defineEnum("bloodSystemMode", BloodSystemMode.BLOOD_AND_HEALTH);
                BUILDER.pop();

                BUILDER.push("Medical Difficulty & Core Systems");
                MEDICAL_DIFFICULTY = BUILDER
                                .comment("Preset for UI and tuning: SQUAD = minimal HUD (vignette; blood + text status in inventory only). REFORGER/HARDCORE = full HUD bars, limb minigames where applicable.",
                                                "Works together with bloodSystemMode — e.g. you can use BLOOD_AND_HEALTH + SQUAD for 'low HP from blood loss' without HP numbers on screen.")
                                .defineEnum("medicalDifficulty", MedicalDifficulty.REFORGER);

                DEBUFFS_ENABLED = BUILDER
                                .comment("Enable debuffs from low blood and high trauma (Slowness, Weakness, etc.).")
                                .define("debuffsEnabled", true);
                BLEEDING_ENABLED = BUILDER
                                .comment("Core bleeding system. Highly recommended for hardcore feel.")
                                .define("bleedingEnabled", true);
                TRAUMA_SYSTEM_ENABLED = BUILDER
                                .comment("Accumulated trauma/shock system (Reforger/Arma style). Causes pain, tunnel vision, unconscious risk.")
                                .define("traumaSystemEnabled", true);
                PAIN_SYSTEM_ENABLED = BUILDER
                                .comment("Separate pain from trauma. Managed by morphine. Adds screen effects.")
                                .define("painSystemEnabled", true);
                CARDIAC_ARREST_ENABLED = BUILDER
                                .comment("If blood critically low AND trauma high, player takes rapid damage (very hardcore).")
                                .define("cardiacArrestEnabled", true);
                BUILDER.pop();

                BUILDER.push("Bleeding & Trauma");
                BASE_BLEED_RATE = BUILDER
                                .comment("Base bleed rate in ml/s for a fresh wound. Scaled by MEDICAL_DIFFICULTY and wound severity. Squad ~2-4, Reforger/Hardcore 8-15+.")
                                .defineInRange("baseBleedRate", 8.0, 0.0, 50.0);
                HEAVY_BLEED_MULTIPLIER = BUILDER
                                .comment("Multiplier for heavy bleeding (after multiple hits or untreated wounds).")
                                .defineInRange("heavyBleedMultiplier", 2.5, 1.0, 5.0);
                WOUND_SEVERITY_TO_BLEED = BUILDER
                                .comment("How much wound severity translates to bleedRate (higher = more realistic bleed from open wounds).")
                                .defineInRange("woundSeverityToBleed", 0.12, 0.0, 0.5);

                TRAUMA_PER_DAMAGE = BUILDER
                                .comment("How much trauma is gained per point of damage taken. Higher = more hardcore (Reforger feel).")
                                .defineInRange("traumaPerDamage", 4.5, 0.0, 20.0);
                TRAUMA_DECAY_RATE = BUILDER
                                .comment("Trauma recovery per second when resting. Very low in HARDCORE mode.")
                                .defineInRange("traumaDecayRate", 0.8, 0.0, 5.0);
                PAIN_DECAY_RATE = BUILDER
                                .comment("How fast pain decreases naturally or with morphine.")
                                .defineInRange("painDecayRate", 1.2, 0.0, 10.0);

                BLOOD_REGEN_RATE = BUILDER
                                .comment("Passive blood regeneration (ml/s) when stable. Set low for hardcore (0.5-2.0).")
                                .defineInRange("bloodRegenRate", 1.5, 0.0, 20.0);
                MORPHINE_DURATION_SECONDS = BUILDER
                                .comment("How long morphine suppresses pain/trauma effects (seconds).")
                                .defineInRange("morphineDurationSeconds", 45.0, 10.0, 180.0);
                BANDAGE_HEAL_AMOUNT = BUILDER
                                .comment("How much wound severity is healed per bandage use.")
                                .defineInRange("bandageHealAmount", 35.0, 10.0, 80.0);
                BANDAGE_HEALS_VANILLA_HEALTH = BUILDER
                                .comment("If true, a successful bandage also restores vanilla Minecraft health (hearts bar).",
                                                "Applied after bleeding is cleared so natural healing rules still apply.")
                                .define("bandageHealsVanillaHealth", true);
                BANDAGE_VANILLA_HEALTH_HEAL = BUILDER
                                .comment("Vanilla health points restored per bandage when bandageHealsVanillaHealth is true.",
                                                "Player max health is usually 20 (10 hearts); 2 = one heart.")
                                .defineInRange("bandageVanillaHealthHeal", 2.0, 0.0, 40.0);
                TOURNIQUET_VANILLA_HEALTH_HEAL = BUILDER
                                .comment("Vanilla health points restored per tourniquet use (simplified medical mode).")
                                .defineInRange("tourniquetVanillaHealthHeal", 1.5, 0.0, 40.0);
                BUILDER.pop();

                BUILDER.push("Medical & Thresholds");
                MEDICAL_MAX_DISTANCE = BUILDER
                                .comment("Max distance to heal other players (blocks).")
                                .defineInRange("medicalMaxDistance", 2.0, 0.5, 10.0);
                MEDICAL_COOLDOWN = BUILDER
                                .comment("Cooldown after healing someone (ticks).")
                                .defineInRange("medicalCooldown", 25, 0, 600);

                CARDIAC_ARREST_BLOOD_THRESHOLD = BUILDER
                                .comment("Blood level below which + high trauma triggers cardiac arrest (if enabled).")
                                .defineInRange("cardiacArrestBloodThreshold", 900.0, 100.0, 2000.0);
                UNCONSCIOUS_TRAUMA_THRESHOLD = BUILDER
                                .comment("Trauma level that risks unconsciousness/ragdoll.")
                                .defineInRange("unconsciousTraumaThreshold", 78.0, 50.0, 95.0);
                CRITICAL_TRAUMA_DEBUFF = BUILDER
                                .comment("Trauma level where strong debuffs (blur, slowness, screen shake) kick in.")
                                .defineInRange("criticalTraumaDebuff", 55.0, 30.0, 85.0);
                BUILDER.pop();

                BUILDER.push("Ragdoll & Visuals");
                DEATH_RAGDOLL_ENABLED = BUILDER
                                .comment("Death ragdoll after lethal damage (visual + hardcore feel).")
                                .define("deathRagdollEnabled", true);
                DEATH_RAGDOLL_DURATION = BUILDER
                                .comment("How long death ragdoll persists (seconds).")
                                .defineInRange("deathRagdollDuration", 18, 0, 120);
                DEATH_RAGDOLL_REACT_TO_FORCES = BUILDER
                                .comment("Whether death ragdolls react to explosions/projectiles.")
                                .define("deathRagdollReactToForces", true);
                DEATH_CORPSE_INTEGRITY = BUILDER
                                .comment("Structural HP of death ragdoll corpse: damage reduces it; heavy hits or explosives can destroy body early.")
                                .defineInRange("deathCorpseIntegrity", 40.0, 1.0, 500.0);
                RAGDOLL_EXPLOSION_IMPULSE_SCALE = BUILDER
                                .comment("Scales impulse from explosions on ragdolls. Lower fixes bodies flying unrealistic distances.")
                                .defineInRange("ragdollExplosionImpulseScale", 0.32, 0.05, 2.0);
                RAGDOLL_EXPLOSION_MAX_IMPULSE = BUILDER
                                .comment("Per-limb impulse cap applied after scaling (bullet central impulse magnitude).")
                                .defineInRange("ragdollExplosionMaxImpulse", 45.0, 5.0, 250.0);
                RAGDOLL_MAX_LINEAR_SPEED = BUILDER
                                .comment("Clamp linear velocity (ragdoll / unconscious).")
                                .defineInRange("ragdollMaxLinearSpeed", 72.0, 10.0, 200.0);
                RAGDOLL_DEATH_CORPSE_MAX_LINEAR_SPEED = BUILDER
                                .comment("Tighter clamp for dead corpses so they tumble instead of soaring.")
                                .defineInRange("ragdollDeathCorpseMaxLinearSpeed", 38.0, 5.0, 120.0);
                RAGDOLL_KNOCKBACK_STRENGTH_CAP = BUILDER
                                .comment("Upper bound on directional knockback strength from melee/damage impulses.")
                                .defineInRange("ragdollKnockbackStrengthCap", 9.0, 1.0, 80.0);
                RAGDOLL_CORPSE_DAMAGE_FACTOR = BUILDER
                                .comment("Corpse integrity drained per damage point when hurtEvents apply to dead player.")
                                .defineInRange("ragdollCorpseDamageFactor", 1.35, 0.1, 10.0);
                RAGDOLL_ORPHAN_EXPLOSION_CORPSE_DAMAGE = BUILDER
                                .comment("Pseudo-damage dealt to orphaned corpse integrity per explosion (no player entity proxy). Scale with blast radius implicitly via impl.")
                                .defineInRange("ragdollOrphanExplosionCorpseDamage", 18.0, 0.0, 200.0);
                RAGDOLL_DEATH_CORPSE_BLOOD_DRIP = BUILDER
                                .comment("Spawn occasional BloodSpray drips downward from death ragdoll limbs.")
                                .define("ragdollDeathCorpseBloodDrip", true);
                RAGDOLL_DEATH_CORPSE_BLOOD_DRIP_INTERVAL = BUILDER
                                .comment("Game ticks between drip attempts (~20 = once per second).")
                                .defineInRange("ragdollDeathCorpseBloodDripInterval", 16, 2, 200);

                ENABLE_HIT_BLUR = BUILDER
                                .comment("Screen blur on hits and low blood/trauma.")
                                .define("enableHitBlur", true);
                HIT_BLUR_USES_POST_PROCESS = BUILDER
                                .comment("If true, hits use a full-screen post-process blur. WARNING: this attaches/detaches",
                                                "Minecraft's post-effect framebuffer every hit, which makes the sky / fog visibly",
                                                "flicker when used together with fog, NVG, shader or Iris/OptiFine-style mods.",
                                                "If false (recommended for modpacks), hits use a short HUD red-flash overlay instead",
                                                "— same feedback, no framebuffer churn, full compatibility with foreign post-shaders.")
                                .define("hitBlurUsesPostProcess", false);
                HIT_BLUR_MULTIPLIER = BUILDER
                                .comment("Intensity of hit blur. Higher = more cinematic.")
                                .defineInRange("hitBlurMultiplier", 1.1, 0.2, 3.0);
                ENABLE_BLOOD_OVERLAY = BUILDER
                                .comment("Red vignette when low on blood.")
                                .define("enableBloodOverlay", true);
                LOW_HEALTH_USES_POST_PROCESS = BUILDER
                                .comment("If true, low health / heavy blood loss applies a full-screen post-process",
                                                "(blur + desaturation). The intensity ramps continuously and the post-chain",
                                                "is held with hysteresis (slow release ~2 s) so the sky / fog no longer",
                                                "flicker every time HP, blood or trauma cross a threshold. Still:",
                                                "Minecraft's GameRenderer can only own one post-effect at a time — when a",
                                                "foreign mod (NVG, thermal, fog) takes the slot, our effect yields silently.",
                                                "If false (default), low HP feedback is delivered only via the red vignette",
                                                "HUD overlay — zero post-shader churn, full compatibility with shader modpacks.")
                                .define("lowHealthUsesPostProcess", false);
                SUPPRESSION_STRENGTH = BUILDER
                                .comment("How strong suppression/slowing is when bleeding (Squad inspired). 0.0-1.0.")
                                .defineInRange("suppressionStrength", 0.65, 0.0, 1.0);
                BUILDER.pop();

                // === CLIENT-only config (per-player display preferences). Stored in
                // config/trauma-client.toml so each client controls their own HUD style
                // independently of the server. This is the file YOU edit on the client. ===
                CLIENT_BUILDER.push("GUI and HUD");
                HUD_PRESENTATION = CLIENT_BUILDER
                                .comment("In-game overlay style (CLIENT-side, applies to your own UI only).",
                                                "AUTO: minimal HUD only when medicalDifficulty = SQUAD (bleed hint only if bleeding).",
                                                "MINIMAL: no on-screen UI elements (no HUD bars, no numbers, no vanilla hearts). Detailed status is shown ONLY in the inventory panel. Immersive visual feedback (red vignette, hit flash, post-shaders, heartbeat audio) still works.",
                                                "FULL: always full bars (HP, blood, trauma) when HUD position is enabled.")
                                .defineEnum("hudPresentation", HudPresentation.AUTO);
                SHOW_VANILLA_HEALTH = CLIENT_BUILDER
                                .comment("Show the vanilla health bar (hearts) above the hotbar.")
                                .define("showVanillaHealth", false);
                HUD_GUI_POSITION = CLIENT_BUILDER
                                .comment("Position of the in-game HUD (outside inventory). Squad/Reforger style tactical HUD recommended.")
                                .defineEnum("hudGuiPosition", GuiPosition.BOTTOM_LEFT);
                INVENTORY_GUI_POSITION = CLIENT_BUILDER
                                .comment("Position of the detailed status panel in inventory.")
                                .defineEnum("inventoryGuiPosition", GuiPosition.BOTTOM_RIGHT);
                CLIENT_BUILDER.pop();
        }

        static final ModConfigSpec SPEC = BUILDER.build();
        static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

        /** Blood loss, bleeding effect, and blood-related HUD/debuffs are active. */
        public static boolean isBloodSystemActive() {
                return BLOOD_SYSTEM_MODE.get() != BloodSystemMode.DISABLED;
        }

        /**
         * Runtime override for HUD presentation, set via /trauma hud-set command.
         * When non-null this takes priority over the value loaded from trauma-common.toml,
         * so users can verify whether bars vanish without having to restart / edit config files.
         */
        private static volatile HudPresentation runtimeHudOverride = null;

        /** Effective HUD presentation: runtime override if set, otherwise the config value. */
        public static HudPresentation effectiveHudPresentation() {
                HudPresentation override = runtimeHudOverride;
                return override != null ? override : HUD_PRESENTATION.get();
        }

        public static void setRuntimeHudOverride(HudPresentation p) {
                runtimeHudOverride = p;
        }

        public static HudPresentation getRuntimeHudOverride() {
                return runtimeHudOverride;
        }

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
        }
}
