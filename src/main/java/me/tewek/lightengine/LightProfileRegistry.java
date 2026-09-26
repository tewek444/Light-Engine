package me.tewek.lightengine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of per-block extended-light profiles.
 * Backed by the COMMON config file (see {@link Config}).
 */
public final class LightProfileRegistry {
    private LightProfileRegistry() {
    }

    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 255;

    public record Profile(int lightRadius, boolean ignoreConditions) {
        public Profile {
            lightRadius = Math.clamp(lightRadius, MIN_RADIUS, MAX_RADIUS);
        }

        public Profile(int lightRadius) {
            this(lightRadius, false);
        }
    }

    private static volatile boolean enabled = true;
    private static volatile String cnflvl = "1.0.0";
    private static final ConcurrentHashMap<ResourceLocation, Profile> PROFILES = new ConcurrentHashMap<>();

    static void apply(boolean enabledValue, String cnflvlValue, Map<ResourceLocation, Profile> fresh) {
        enabled = enabledValue;
        cnflvl = cnflvlValue == null ? "1.0.0" : cnflvlValue;
        PROFILES.clear();
        for (Map.Entry<ResourceLocation, Profile> e : fresh.entrySet()) {
            Profile profile = e.getValue() == null ? new Profile(0, false) : e.getValue();
            PROFILES.put(e.getKey(), profile);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String cnflvl() {
        return cnflvl;
    }

    public static Profile get(ResourceLocation id) {
        return PROFILES.get(id);
    }

    public static Profile get(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? null : PROFILES.get(id);
    }

    public static Profile get(BlockState state) {
        return get(state.getBlock());
    }

    public static boolean isProfiled(Block block) {
        return get(block) != null;
    }

    public static boolean isProfiled(BlockState state) {
        return get(state) != null;
    }

    /**
     * State-aware emission for the hooks. Without the ignore flag, vanilla
     * conditions are respected: blocks with a LIT or POWERED property
     * (lamps, furnaces, campfires, candles, copper bulbs) emit the profiled
     * radius only while lit/powered and fall back to vanilla otherwise.
     * With the flag (or without such a property) the radius always applies.
     */
    public static int emissionForState(BlockState state, int original) {
        if (!enabled) {
            return original;
        }
        Profile p = get(state);
        if (p == null) {
            return original;
        }
        if (p.ignoreConditions()) {
            return p.lightRadius();
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT)) ? p.lightRadius() : original;
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return Boolean.TRUE.equals(state.getValue(BlockStateProperties.POWERED)) ? p.lightRadius() : original;
        }
        return p.lightRadius();
    }

    public static Map<ResourceLocation, Profile> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(PROFILES));
    }

    /** Inserts or replaces a single profile, keeping enabled/cnflvl. Returns the clamped radius. */
    public static int upsert(ResourceLocation id, int radius, boolean ignoreConditions) {
        int clamped = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        Map<ResourceLocation, Profile> merged = new HashMap<>(PROFILES);
        merged.put(id, new Profile(clamped, ignoreConditions));
        apply(isEnabled(), cnflvl(), merged);
        return clamped;
    }

    /** Removes a single profile (falls back to vanilla emission). Returns whether one existed. */
    public static boolean remove(ResourceLocation id) {
        if (id == null || !PROFILES.containsKey(id)) {
            return false;
        }
        Map<ResourceLocation, Profile> merged = new HashMap<>(PROFILES);
        merged.remove(id);
        apply(isEnabled(), cnflvl(), merged);
        return true;
    }
}
