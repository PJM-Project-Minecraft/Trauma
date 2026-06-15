package ru.liko.trauma.ragdoll;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import ru.liko.trauma.Config;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import ru.liko.trauma.bloodybits.registry.ModEntityTypes;
import ru.liko.trauma.bloodybits.utils.BloodyBitsUtils;

/**
 * Server-side decorative blood dripping from ragdoll corpses ({@link BloodSprayEntity}).
 */
public final class RagdollCorpseBlood {

    private RagdollCorpseBlood() {}

    public static void trySpawnSlowDrip(ServerLevel level, Vec3 pos, RandomSource random) {
        if (!Config.RAGDOLL_DEATH_CORPSE_BLOOD_DRIP.get())
            return;

        while (BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.size() >= CommonConfig.maxSpatters()) {
            BloodSprayEntity oldest = BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.pollFirst();
            if (oldest != null)
                oldest.discard();
        }

        BloodSprayEntity e = new BloodSprayEntity(
                ModEntityTypes.BLOOD_SPRAY.get(),
                pos.x + (random.nextFloat() - 0.5f) * 0.08,
                pos.y + random.nextFloat() * 0.06,
                pos.z + (random.nextFloat() - 0.5f) * 0.08,
                level);
        /* Without {@code ownerName} the projectile self-deletes immediately on tick. */
        e.ownerName = "player";

        double vx = (random.nextFloat() - 0.5f) * 0.035;
        double vz = (random.nextFloat() - 0.5f) * 0.035;
        double vy = -0.045 - random.nextFloat() * 0.07;
        e.setDeltaMovement(new Vec3(vx, vy, vz));

        BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.add(e);
        level.addFreshEntity(e);
    }
}
