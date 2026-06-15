package ru.liko.trauma.bloodybits.config;

import com.google.common.reflect.TypeToken;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

public class CommonConfig {
    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();

    private static final String BLOOD_BLACK = "#323232";
    private static final String BLOOD_BLUE = "#2acbf7";
    private static final String BLOOD_GREEN = "#01c801";
    private static final String BLOOD_GREY = "#c8c8c8";
    private static final String BLOOD_PURPLE = "#c832ff";
    private static final String BLOOD_ORANGE = "#fac832";

    private static final List<String> BLOOD_BLACK_ENTITIES = List.of("minecraft:wither_skeleton", "minecraft:wither");
    private static final List<String> BLOOD_BLUE_ENTITIES = List.of("minecraft:allay", "minecraft:warden");
    private static final List<String> BLOOD_GREEN_ENTITIES = List.of("minecraft:spider", "minecraft:cave_spider",
            "minecraft:creeper", "minecraft:bee", "minecraft:slime");
    private static final List<String> BLOOD_GREY_ENTITIES = List.of("minecraft:skeleton", "minecraft:skeleton_horse",
            "minecraft:snow_golem", "minecraft:shulker", "minecraft:stray");
    private static final List<String> BLOOD_PURPLE_ENTITIES = List.of("minecraft:enderman", "minecraft:shulker",
            "minecraft:ender_dragon", "minecraft:endermite");
    private static final List<String> BLOOD_ORANGE_ENTITIES = List.of("minecraft:magma_cube", "minecraft:blaze");
    private static final HashMap<String, List<String>> DEFAULT_ENTITY_BLOOD_COLORS = new HashMap<>();
    private static HashMap<String, List<String>> ENTITY_BLOOD_COLORS;

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static ModConfigSpec.BooleanValue BLEED_WHEN_DAMAGED;
    private static ModConfigSpec.IntValue DESPAWN_TIME;
    private static ModConfigSpec.IntValue MAX_SPATTERS;
    private static ModConfigSpec.DoubleValue BLOOD_SPRAY_DISTANCE;
    private static ModConfigSpec.DoubleValue BLOOD_SPATTER_VOLUME;

    private static ModConfigSpec.ConfigValue<List<? extends String>> SOLID_ENTITIES;
    private static ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ENTITIES;
    private static ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_DAMAGE_SOURCES;

    public static boolean bleedWhenDamaged() {
        return BLEED_WHEN_DAMAGED.get();
    }

    public static int despawnTime() {
        return DESPAWN_TIME.get();
    }

    public static int maxSpatters() {
        return MAX_SPATTERS.get();
    }

    public static double bloodSpatterVolume() {
        return BLOOD_SPATTER_VOLUME.get();
    }

    public static double bloodSprayDistance() {
        return BLOOD_SPRAY_DISTANCE.get();
    }

    public static List<? extends String> solidEntities() {
        return SOLID_ENTITIES.get();
    }

    public static List<? extends String> blackListEntities() {
        return BLACKLIST_ENTITIES.get();
    }

    public static List<? extends String> blackListDamageSources() {
        return BLACKLIST_DAMAGE_SOURCES.get();
    }

    public static HashMap<String, List<String>> entityBloodColors() {
        return ENTITY_BLOOD_COLORS;
    }

    static {
        BUILDER.push("blood spray settings");

        DESPAWN_TIME = BUILDER.comment("How long in ticks (20 ticks = 1 second) until a blood spatter despawns.")
                .defineInRange("despawn_time", 2000, 0, 100000);
        BLEED_WHEN_DAMAGED = BUILDER.comment(
                "Do entities bleed when damaged below 50% health. The more they are damaged, the more often they bleed.")
                .define("bleed_when_damaged", false);
        MAX_SPATTERS = BUILDER.comment("The maximum amount of blood spatters that can exist in the world at once.")
                .defineInRange("max_spatters", 500, 0, 10000);
        BLOOD_SPATTER_VOLUME = BUILDER.comment("How loud the blood spatters are.")
                .defineInRange("blood_spatter_volume", 0.75, 0, 1.0);
        BLOOD_SPRAY_DISTANCE = BUILDER
                .comment("How far blood will spray from an entity. Higher values mean blood sprays farther away.")
                .defineInRange("blood_spray_distance", 0.025, 0, 1.0);
        SOLID_ENTITIES = BUILDER.comment(
                "Define what mobs 'bleed' solid bits. This is mainly skeletons. Instead of bleeding they will just shoot out colored bits,"
                        +
                        "and instead of getting bloodier when damaged, they will lose pixels.")
                .defineListAllowEmpty("solid_entities",
                        List.of("minecraft:skeleton", "minecraft:skeleton_horse", "minecraft:wither_skeleton",
                                "minecraft:wither", "minecraft:shulker", "minecraft:iron_golem", "minecraft:stray"),
                        it -> it instanceof String);
        BLACKLIST_ENTITIES = BUILDER.comment(
                "Some mobs don't play nice with this mod, and may cause crashes. Define which mobs you want to blacklist here (enter 'player' for all players).")
                .defineListAllowEmpty("blacklist_entities",
                        List.of("alexsmobs:cachalot_whale"),
                        it -> it instanceof String);
        BLACKLIST_DAMAGE_SOURCES = BUILDER
                .comment("Define what damage sources will be blacklisted from producing blood sprays or explosions.")
                .defineListAllowEmpty("blacklist_damage_sources",
                        List.of("onFire", "inFire", "starve", "drown", "hotFloor", "dragonBreath", "dryOut", "freeze",
                                "lava"),
                        it -> it instanceof String);

        BUILDER.pop();

        ENTITY_BLOOD_COLORS = getConfigData();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static HashMap<String, List<String>> getOrCreateConfigFile(File configFile, Type type) {

        if (!configFile.exists()) {
            try {
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_BLACK, BLOOD_BLACK_ENTITIES);
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_BLUE, BLOOD_BLUE_ENTITIES);
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_GREEN, BLOOD_GREEN_ENTITIES);
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_GREY, BLOOD_GREY_ENTITIES);
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_PURPLE, BLOOD_PURPLE_ENTITIES);
                DEFAULT_ENTITY_BLOOD_COLORS.put(BLOOD_ORANGE, BLOOD_ORANGE_ENTITIES);
                FileUtils.write(configFile, GSON.toJson(CommonConfig.DEFAULT_ENTITY_BLOOD_COLORS),
                        Charset.defaultCharset());
            } catch (IOException e) {
                // ignore
            }
        }

        try {
            return GSON.fromJson(FileUtils.readFileToString(configFile, Charset.defaultCharset()), type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File getConfigDirectory() {
        Path configPath = FMLPaths.CONFIGDIR.get();
        Path jsonPath = Paths.get(configPath.toAbsolutePath().toString(), "bloodybits_colors");
        return jsonPath.toFile();
    }

    private static HashMap<String, List<String>> getConfigData() {
        File configDir = getConfigDirectory();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File entityBloodColorsConfigFile = new File(configDir, "entity_blood_colors" + ".json");
        return getOrCreateConfigFile(entityBloodColorsConfigFile, new TypeToken<HashMap<String, List<String>>>() {
        }.getType());
    }
}
