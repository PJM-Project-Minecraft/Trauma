package ru.liko.trauma.ragdoll;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent storage for death ragdolls.
 * Saves ragdoll state (transforms, velocities, timer) to the world data folder
 * so death ragdolls survive world save/load cycles.
 */
public class RagdollSavedData extends SavedData {

    private static final String DATA_NAME = "trauma_ragdolls";

    private final List<SavedDeathRagdoll> savedRagdolls = new ArrayList<>();

    public RagdollSavedData() {
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (SavedDeathRagdoll ragdoll : savedRagdolls) {
            list.add(ragdoll.toTag());
        }
        tag.put("DeathRagdolls", list);
        return tag;
    }

    private static RagdollSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RagdollSavedData data = new RagdollSavedData();
        ListTag list = tag.getList("DeathRagdolls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.savedRagdolls.add(SavedDeathRagdoll.fromTag(entry));
        }
        return data;
    }

    /**
     * Collect all active death ragdolls from a PhysicsWorld instance and store for saving.
     */
    public void collectFromPhysicsWorld(PhysicsWorld pw) {
        savedRagdolls.clear();

        // Collect from orphaned death ragdolls
        for (PlayerRagdoll ragdoll : pw.getOrphanedDeathRagdolls()) {
            if (ragdoll.hasBodies() && ragdoll.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL) {
                savedRagdolls.add(SavedDeathRagdoll.fromPlayerRagdoll(ragdoll));
            }
        }

        // Collect from active player ragdolls that are in death mode
        for (PlayerRagdoll ragdoll : pw.getAllActivePlayerRagdolls()) {
            if (ragdoll.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL && ragdoll.hasBodies()) {
                // Avoid duplicates (already collected from orphaned list)
                boolean alreadyCollected = savedRagdolls.stream()
                        .anyMatch(s -> s.playerUUID.equals(ragdoll.getPlayerUUID())
                                && s.networkRagdollId == ragdoll.getNetworkRagdollId());
                if (!alreadyCollected) {
                    savedRagdolls.add(SavedDeathRagdoll.fromPlayerRagdoll(ragdoll));
                }
            }
        }

        setDirty();
    }

    public List<SavedDeathRagdoll> getSavedRagdolls() {
        return savedRagdolls;
    }

    public void clearSavedRagdolls() {
        savedRagdolls.clear();
        setDirty();
    }

    /**
     * Get or create the SavedData for a specific dimension.
     */
    public static RagdollSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(RagdollSavedData::new, RagdollSavedData::load),
                DATA_NAME
        );
    }

    // ======================== SERIALIZABLE RAGDOLL STATE ========================

    public static class SavedDeathRagdoll {
        public final UUID playerUUID;
        public final int networkRagdollId;
        public final int deathTicksRemaining;
        public final RagdollTransform[] transforms;

        public SavedDeathRagdoll(UUID uuid, int networkId, int ticks, RagdollTransform[] transforms) {
            this.playerUUID = uuid;
            this.networkRagdollId = networkId;
            this.deathTicksRemaining = ticks;
            this.transforms = transforms;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", playerUUID);
            tag.putInt("NetworkId", networkRagdollId);
            tag.putInt("TicksRemaining", deathTicksRemaining);

            ListTag transformList = new ListTag();
            for (RagdollTransform t : transforms) {
                CompoundTag tTag = new CompoundTag();
                tTag.putInt("PartId", t.partId);
                tTag.putFloat("PosX", t.position.x);
                tTag.putFloat("PosY", t.position.y);
                tTag.putFloat("PosZ", t.position.z);
                tTag.putFloat("RotX", t.rotation.x);
                tTag.putFloat("RotY", t.rotation.y);
                tTag.putFloat("RotZ", t.rotation.z);
                tTag.putFloat("RotW", t.rotation.w);
                tTag.putFloat("VelX", t.velocity.x);
                tTag.putFloat("VelY", t.velocity.y);
                tTag.putFloat("VelZ", t.velocity.z);
                transformList.add(tTag);
            }
            tag.put("Transforms", transformList);
            return tag;
        }

        public static SavedDeathRagdoll fromTag(CompoundTag tag) {
            UUID uuid = tag.getUUID("UUID");
            int networkId = tag.getInt("NetworkId");
            int ticks = tag.getInt("TicksRemaining");

            ListTag transformList = tag.getList("Transforms", Tag.TAG_COMPOUND);
            RagdollTransform[] transforms = new RagdollTransform[transformList.size()];
            for (int i = 0; i < transformList.size(); i++) {
                CompoundTag tTag = transformList.getCompound(i);
                transforms[i] = new RagdollTransform(
                        tTag.getInt("PartId"),
                        tTag.getFloat("PosX"), tTag.getFloat("PosY"), tTag.getFloat("PosZ"),
                        tTag.getFloat("RotX"), tTag.getFloat("RotY"), tTag.getFloat("RotZ"), tTag.getFloat("RotW"),
                        tTag.getFloat("VelX"), tTag.getFloat("VelY"), tTag.getFloat("VelZ")
                );
            }
            return new SavedDeathRagdoll(uuid, networkId, ticks, transforms);
        }

        public static SavedDeathRagdoll fromPlayerRagdoll(PlayerRagdoll ragdoll) {
            return new SavedDeathRagdoll(
                    ragdoll.getPlayerUUID(),
                    ragdoll.getNetworkRagdollId(),
                    ragdoll.getDeathTicksRemaining(),
                    ragdoll.getRagdollTransforms()
            );
        }
    }
}
