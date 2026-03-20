package ru.liko.trauma;

import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.effect.ModEffects;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.registry.ModEntityTypes;

import ru.liko.trauma.common.item.ModItems;
import ru.liko.trauma.common.item.ModCreativeTab;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import ru.liko.trauma.bloodybits.client.render.BloodSprayRenderer;
import ru.liko.trauma.bloodybits.client.render.layer.InjuryLayer;
import ru.liko.trauma.compat.tacz.TaczCompat;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Trauma.MODID)
public class Trauma {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "trauma";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod
    // is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and
    // pass them in automatically.

    public Trauma(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register custom effects
        ModEffects.EFFECTS.register(modEventBus);
        // Register custom capability attachments
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        // Register custom items
        ModItems.ITEMS.register(modEventBus);
        // Register custom creative tab
        ModCreativeTab.CREATIVE_TABS.register(modEventBus);
        // Register custom entities (BloodyBits)
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        // Register custom sounds
        ru.liko.trauma.common.sound.ModSounds.SOUNDS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Trauma) to
        // respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in
        // this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register TaCZ compatibility if the mod is loaded
        if (ModList.get().isLoaded("tacz")) {
            TaczCompat.register();
            LOGGER.info("TaCZ compatibility loaded!");
        }

        // Register our mod's ModConfigSpec so that FML can create and load the config
        // file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "bloodybits-common.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods
    // in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void registerKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(ru.liko.trauma.client.input.KeyBindings.SUPPRESS_KEY);
            event.register(ru.liko.trauma.client.input.KeyBindings.FIX_DISLOCATION_KEY);
        }

        @SubscribeEvent
        public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
            ru.liko.trauma.client.overlay.ModOverlays.registerGuiLayers(event);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.BLOOD_SPRAY.get(), BloodSprayRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.MANNEQUIN.get(),
                    ru.liko.trauma.client.render.MannequinRenderer::new);
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                LivingEntityRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new InjuryLayer(renderer));
                    renderer.addLayer(new ru.liko.trauma.client.render.SuppressionArmLayer(renderer));
                }
            }

            for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                try {
                    var renderer = event.getRenderer(type);
                    if (renderer instanceof LivingEntityRenderer) {
                        @SuppressWarnings({ "rawtypes", "unchecked" })
                        LivingEntityRenderer livingRenderer = (LivingEntityRenderer) renderer;
                        livingRenderer.addLayer(new InjuryLayer(livingRenderer));
                    }
                } catch (Exception ignore) {
                    // Ignore entities that don't have a registered renderer or aren't LivingEntity
                }
            }
        }
    }
}
