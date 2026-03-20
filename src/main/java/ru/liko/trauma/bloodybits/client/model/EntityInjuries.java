package ru.liko.trauma.bloodybits.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.utils.BloodyBitsUtils;
import java.util.*;

public class EntityInjuries {

    private int smallBleedHits;
    private int mediumBleedHits;
    private int largeBleedHits;

    private int smallBurnHits;
    private int mediumBurnHits;
    private int largeBurnHits;

    private int smallHealAmount;
    private int mediumHealAmount;
    private int largeHealAmount;

    public List<ResourceLocation> availableSmallInjuries = new ArrayList<>();
    public List<ResourceLocation> availableMediumInjuries = new ArrayList<>();
    public List<ResourceLocation> availableLargeInjuries = new ArrayList<>();

    public Map<ResourceLocation, String> appliedSmallInjuries = new HashMap<>();
    public Map<ResourceLocation, String> appliedMediumInjuries = new HashMap<>();
    public Map<ResourceLocation, String> appliedLargeInjuries = new HashMap<>();

    public EntityInjuries(String entityName) {
        String namespace;

        if (entityName.equals("player")) {
            namespace = "minecraft";
        } else {
            namespace = BloodyBitsUtils.decompose(entityName, ':')[0];
            entityName = BloodyBitsUtils.decompose(entityName, ':')[1];
        }

        String path = "textures/entity/" + entityName + "/";

        this.addEntityDamageTexture(namespace, path + "small_injuries/");
        this.addEntityDamageTexture(namespace, path + "medium_injuries/");
        this.addEntityDamageTexture(namespace, path + "large_injuries/");
    }

    private void addEntityDamageTexture(String namespace, String path) {

        for (int i = 0; i < CommonConfig.availableTexturesPerEntity(); i++) {
            String modifiedPath = path.concat(i + ".png");
            try {
                ResourceLocation injuryTextureResourceLocation = ResourceLocation.fromNamespaceAndPath(namespace,
                        modifiedPath);

                // Assert it exists, otherwise it will throw NoSuchElementException and break
                // the loop
                Minecraft.getInstance().getResourceManager().getResource(injuryTextureResourceLocation).orElseThrow();

                if (path.contains("small")) {
                    this.availableSmallInjuries.add(injuryTextureResourceLocation);
                } else if (path.contains("medium")) {
                    this.availableMediumInjuries.add(injuryTextureResourceLocation);
                } else if (path.contains("large")) {
                    this.availableLargeInjuries.add(injuryTextureResourceLocation);
                }
            } catch (Exception ignore) {
                break;
            }
        }
    }

    public void addInjuryHits(String injuryType, double entityDamagePercentage) {

        if (entityDamagePercentage >= 0.15) {
            switch (injuryType) {
                case "bleed" -> this.largeBleedHits++;
                case "burn" -> this.largeBurnHits++;
            }
        } else if (entityDamagePercentage >= 0.05) {
            switch (injuryType) {
                case "bleed" -> this.mediumBleedHits++;
                case "burn" -> this.mediumBurnHits++;
            }
        } else {
            switch (injuryType) {
                case "bleed" -> this.smallBleedHits++;
                case "burn" -> this.smallBurnHits++;
            }
        }

        if (this.availableLargeInjuries.isEmpty() && this.largeBleedHits >= 1) {
            this.mediumBleedHits += (this.largeBleedHits * 2);
            this.largeBleedHits = 0;
        }

        if (this.availableSmallInjuries.isEmpty() && this.smallBleedHits >= 3) {
            this.mediumBleedHits += (this.smallBleedHits / 3);
            this.smallBleedHits = 0;
        }

        if (this.availableMediumInjuries.isEmpty()) {
            if (!this.availableLargeInjuries.isEmpty() && this.mediumBleedHits >= 2) {
                this.largeBleedHits += (this.mediumBleedHits / 2);
                this.mediumBleedHits = 0;
            } else if (!this.availableSmallInjuries.isEmpty() && this.mediumBleedHits >= 1) {
                this.smallBleedHits += (this.mediumBleedHits * 2);
                this.mediumBleedHits = 0;
            }
        }

        if (this.availableLargeInjuries.isEmpty() && this.largeBurnHits >= 1) {
            this.mediumBurnHits += (this.largeBurnHits * 2);
            this.largeBurnHits = 0;
        }

        if (this.availableSmallInjuries.isEmpty() && this.smallBurnHits >= 3) {
            this.mediumBurnHits += (this.smallBurnHits / 3);
            this.smallBurnHits = 0;
        }

        if (this.availableMediumInjuries.isEmpty()) {
            if (!this.availableLargeInjuries.isEmpty() && this.mediumBurnHits >= 2) {
                this.largeBurnHits += (this.mediumBurnHits / 2);
                this.mediumBurnHits = 0;
            } else if (!this.availableSmallInjuries.isEmpty() && this.mediumBurnHits >= 1) {
                this.smallBurnHits += (this.mediumBurnHits * 2);
                this.mediumBurnHits = 0;
            }
        }

        this.updateInjuries(injuryType);
    }

    public void addHealAmount(double entityHealPercentage) {
        if (entityHealPercentage >= 0.15) {
            this.largeHealAmount++;
        } else if (entityHealPercentage >= 0.05) {
            this.mediumHealAmount++;
        } else {
            this.smallHealAmount++;
        }

        if (this.appliedLargeInjuries.isEmpty() && this.largeHealAmount >= 1) {
            this.mediumHealAmount += (this.largeHealAmount * 2);
            this.largeHealAmount = 0;
        }

        if (this.appliedSmallInjuries.isEmpty() && this.smallHealAmount >= 3) {
            this.mediumHealAmount += (this.smallHealAmount / 3);
            this.smallHealAmount = 0;
        }

        if (this.appliedMediumInjuries.isEmpty()) {
            if (!this.appliedLargeInjuries.isEmpty() && this.mediumHealAmount >= 2) {
                this.largeHealAmount += (this.mediumHealAmount / 2);
                this.mediumHealAmount = 0;
            } else if (!this.appliedSmallInjuries.isEmpty() && this.mediumHealAmount >= 1) {
                this.smallHealAmount += (this.mediumHealAmount * 2);
                this.mediumHealAmount = 0;
            }
        }

        this.updateHealInjuries();
    }

    private void updateHealInjuries() {
        if (this.largeHealAmount >= 1) {
            this.healInjuries(this.availableLargeInjuries, this.appliedLargeInjuries);
            this.largeHealAmount--;
        }

        if (this.mediumHealAmount >= 2) {
            this.healInjuries(this.availableMediumInjuries, this.appliedMediumInjuries);
            this.mediumHealAmount -= 2;
        }

        if (this.smallHealAmount >= 3) {
            this.healInjuries(this.availableSmallInjuries, this.appliedSmallInjuries);
            this.smallHealAmount -= 3;
        }
    }

    private void healInjuries(List<ResourceLocation> availableSizeInjuries,
            Map<ResourceLocation, String> injurySizeMap) {
        if (!injurySizeMap.isEmpty()) {
            ResourceLocation firstInjury = injurySizeMap.entrySet().stream().findFirst().get().getKey();
            if (firstInjury != null) {
                availableSizeInjuries.add(firstInjury);
                injurySizeMap.remove(firstInjury);
            }
        }
    }

    private void updateInjuries(String injuryType) {
        if (this.largeBleedHits >= 1) {
            this.updateInjuriesList(injuryType, this.availableLargeInjuries, this.appliedLargeInjuries);
            this.largeBleedHits--;
        } else if (this.mediumBleedHits >= 2) {
            this.updateInjuriesList(injuryType, this.availableMediumInjuries, this.appliedMediumInjuries);
            this.mediumBleedHits -= 2;
        } else if (this.smallBleedHits >= 3) {
            this.updateInjuriesList(injuryType, this.availableSmallInjuries, this.appliedSmallInjuries);
            this.smallBleedHits -= 3;
        }

        if (this.largeBurnHits >= 1) {
            this.updateInjuriesList(injuryType, this.availableLargeInjuries, this.appliedLargeInjuries);
            this.largeBurnHits--;
        } else if (this.mediumBurnHits >= 2) {
            this.updateInjuriesList(injuryType, this.availableMediumInjuries, this.appliedMediumInjuries);
            this.mediumBurnHits -= 2;
        } else if (this.smallBurnHits >= 3) {
            this.updateInjuriesList(injuryType, this.availableSmallInjuries, this.appliedSmallInjuries);
            this.smallBurnHits -= 3;
        }
    }

    private void updateInjuriesList(String injuryType, List<ResourceLocation> availableSizeInjuries,
            Map<ResourceLocation, String> injurySizeMap) {
        if (!availableSizeInjuries.isEmpty()) {
            int randomIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(availableSizeInjuries.size());
            ResourceLocation randomInjury = availableSizeInjuries.get(randomIndex);
            injurySizeMap.put(randomInjury, injuryType);
            availableSizeInjuries.remove(randomIndex);
        }
    }
}
