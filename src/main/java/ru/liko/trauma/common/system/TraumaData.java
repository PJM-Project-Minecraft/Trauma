package ru.liko.trauma.common.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TraumaData(float bloodVolume, int bleedStrength,
        int hitZoneOrdinal, float blurIntensity, float legFracture, boolean hasSplint, float legDislocation) {

    public static final float MAX_BLOOD = 5000f; // 5000 ml

    public static final Codec<TraumaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("bloodVolume", MAX_BLOOD).forGetter(TraumaData::bloodVolume),
            Codec.INT.optionalFieldOf("bleedStrength", 0).forGetter(TraumaData::bleedStrength),
            Codec.INT.optionalFieldOf("hitZoneOrdinal", -1).forGetter(TraumaData::hitZoneOrdinal),
            Codec.FLOAT.optionalFieldOf("blurIntensity", 0f).forGetter(TraumaData::blurIntensity),
            Codec.FLOAT.optionalFieldOf("legFracture", 0f).forGetter(TraumaData::legFracture),
            Codec.BOOL.optionalFieldOf("hasSplint", false).forGetter(TraumaData::hasSplint),
            Codec.FLOAT.optionalFieldOf("legDislocation", 0f).forGetter(TraumaData::legDislocation))
            .apply(instance, TraumaData::new));

    public static TraumaData createDefault() {
        return new TraumaData(MAX_BLOOD, 0, -1, 0f, 0f, false, 0f);
    }

    public TraumaData withBlood(float newBlood) {
        return new TraumaData(Math.clamp(newBlood, 0, MAX_BLOOD), bleedStrength,
                hitZoneOrdinal, blurIntensity, legFracture, hasSplint, legDislocation);
    }

    public TraumaData withBleedStrength(int newStrength) {
        return new TraumaData(bloodVolume, newStrength, hitZoneOrdinal, blurIntensity, legFracture, hasSplint, legDislocation);
    }

    public TraumaData withRecentHitZone(HitZone zone) {
        return new TraumaData(bloodVolume, bleedStrength,
                zone == null ? -1 : zone.ordinal(), blurIntensity, legFracture, hasSplint, legDislocation);
    }

    public TraumaData withBlur(float newBlur) {
        return new TraumaData(bloodVolume, bleedStrength, hitZoneOrdinal,
                Math.clamp(newBlur, 0f, 1f), legFracture, hasSplint, legDislocation);
    }

    public TraumaData withLegFracture(float newFracture) {
        return new TraumaData(bloodVolume, bleedStrength, hitZoneOrdinal, blurIntensity,
                Math.clamp(newFracture, 0f, 100f), hasSplint, legDislocation);
    }

    public TraumaData withSplint(boolean splint) {
        return new TraumaData(bloodVolume, bleedStrength, hitZoneOrdinal, blurIntensity, legFracture, splint, legDislocation);
    }

    public TraumaData withLegDislocation(float newDislocation) {
        return new TraumaData(bloodVolume, bleedStrength, hitZoneOrdinal, blurIntensity,
                legFracture, hasSplint, Math.clamp(newDislocation, 0f, 100f));
    }

    public HitZone getRecentHitZone() {
        if (hitZoneOrdinal < 0 || hitZoneOrdinal >= HitZone.values().length) {
            return null;
        }
        return HitZone.values()[hitZoneOrdinal];
    }
}
