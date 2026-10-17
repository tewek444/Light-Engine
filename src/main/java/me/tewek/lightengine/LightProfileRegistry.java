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
 *
 * <p>Two layers: profiles from the COMMON config file (see {@link Config},
 * synced from the server) always win; below them sit defaults other mods
 * registered through {@link LightEngineApi} (full profiles and per-state
 * color providers), identical on both sides because mods register them in
 * common setup.</p>
 */
public final class LightProfileRegistry {
    private LightProfileRegistry() {
    }

    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 255;
    /**
     * Effective emission cap. Profiles still store 0-255 (config/slider
     * untouched), but neither the vanilla wave nor the chroma flood may
     * propagate further: a 255-box is ~100M cells and kills the server
     * thread (freeze, then watchdog/OOM). 48 is 3x vanilla visibility and
     * keeps the worst box under ~1M cells. Larger halos need an async
     * flood (follow-up), not a sync one.
     */
    public static final int EFFECTIVE_MAX_RADIUS = MAX_RADIUS;
    /** Default emitter tint: Photon medium-golden (#FF914C), matches torch. */
    public static final int DEFAULT_COLOR = 0xFF914C;

    public record Profile(int lightRadius, boolean ignoreConditions, int color, boolean tintEnabled) {
        public Profile {
            lightRadius = Math.clamp(lightRadius, MIN_RADIUS, MAX_RADIUS);
            color = color & 0xFFFFFF;
        }

        /** Legacy shape: tint on (preserves every existing call site). */
        public Profile(int lightRadius, boolean ignoreConditions, int color) {
            this(lightRadius, ignoreConditions, color, true);
        }

        public Profile(int lightRadius) {
            this(lightRadius, false, DEFAULT_COLOR, true);
        }

        public Profile(int lightRadius, boolean ignoreConditions) {
            this(lightRadius, ignoreConditions, DEFAULT_COLOR, true);
        }
    }

    private static volatile boolean enabled = true;
    private static volatile String cnflvl = "1.0.0";
    private static final ConcurrentHashMap<ResourceLocation, Profile> PROFILES = new ConcurrentHashMap<>();
    /** Mod-registered defaults (below config profiles). */
    private static final ConcurrentHashMap<ResourceLocation, Profile> API_DEFAULTS = new ConcurrentHashMap<>();
    /** Mod-registered per-state colors (radius stays vanilla unless profiled). */
    private static final ConcurrentHashMap<ResourceLocation, LightEngineApi.ColorProvider> COLOR_PROVIDERS =
            new ConcurrentHashMap<>();

    static void registerApiDefault(ResourceLocation id, Profile profile) {
        API_DEFAULTS.put(id, profile);
    }

    static void registerColorProvider(ResourceLocation id, LightEngineApi.ColorProvider provider) {
        COLOR_PROVIDERS.put(id, provider);
    }

    static boolean unregisterApi(ResourceLocation id) {
        boolean a = API_DEFAULTS.remove(id) != null;
        boolean b = COLOR_PROVIDERS.remove(id) != null;
        return a || b;
    }

    /** Mod-registered defaults (read-only view, for tools and dumps). */
    public static Map<ResourceLocation, Profile> apiDefaults() {
        return Collections.unmodifiableMap(new HashMap<>(API_DEFAULTS));
    }

    static void apply(boolean enabledValue, String cnflvlValue, Map<ResourceLocation, Profile> fresh) {
        enabled = enabledValue;
        cnflvl = cnflvlValue == null ? "1.0.0" : cnflvlValue;
        PROFILES.clear();
        for (Map.Entry<ResourceLocation, Profile> e : fresh.entrySet()) {
            Profile profile = e.getValue() == null ? new Profile(0, false) : e.getValue();
            PROFILES.put(e.getKey(), profile);
        }
    }

    public static boolean isEmpty() {
        return PROFILES.isEmpty() && API_DEFAULTS.isEmpty() && COLOR_PROVIDERS.isEmpty();
    }

    /**
     * Applies an authoritative snapshot from the server (profile sync).
     * Registry only: never touches the file, never touches enabled/cnflvl.
     */
    public static void applySnapshot(Map<ResourceLocation, Profile> fresh) {
        Map<ResourceLocation, Profile> clean = new HashMap<>();
        if (fresh != null) {
            for (Map.Entry<ResourceLocation, Profile> e : fresh.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                Profile p = e.getValue() == null ? new Profile(0, false, DEFAULT_COLOR) : e.getValue();
                clean.put(e.getKey(), new Profile(p.lightRadius(), p.ignoreConditions(), p.color(),
                        p.tintEnabled()));
            }
        }
        PROFILES.clear();
        PROFILES.putAll(clean);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String cnflvl() {
        return cnflvl;
    }

    /** Effective profile: config first, then mod-registered default. */
    public static Profile get(ResourceLocation id) {
        Profile p = PROFILES.get(id);
        return p != null ? p : API_DEFAULTS.get(id);
    }

    public static Profile get(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? null : get(id);
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
        int capped = Math.min(p.lightRadius(), EFFECTIVE_MAX_RADIUS);
        if (p.ignoreConditions()) {
            return capped;
        }
        if (state.hasProperty(BlockStateProperties.CANDLES)) {
            // The radius is for four candles; fewer candles shine less, never below vanilla.
            capped = Math.max(original, Math.round(capped * state.getValue(BlockStateProperties.CANDLES) / 4.0F));
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT)) ? capped : original;
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return Boolean.TRUE.equals(state.getValue(BlockStateProperties.POWERED)) ? capped : original;
        }
        return capped;
    }

    public static Map<ResourceLocation, Profile> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(PROFILES));
    }

    /** Inserts or replaces a single profile, keeping enabled/cnflvl. Returns the clamped radius. */
    public static int upsert(ResourceLocation id, int radius, boolean ignoreConditions) {
        Profile existing = id == null ? null : PROFILES.get(id);
        int color = existing == null ? DEFAULT_COLOR : existing.color();
        boolean tint = existing == null || existing.tintEnabled();
        return upsert(id, radius, ignoreConditions, color, tint);
    }

    /** Inserts or replaces a single profile with tint, keeping enabled/cnflvl. Returns the clamped radius. */
    public static int upsert(ResourceLocation id, int radius, boolean ignoreConditions, int color) {
        Profile existing = id == null ? null : PROFILES.get(id);
        boolean tint = existing == null || existing.tintEnabled();
        return upsert(id, radius, ignoreConditions, color, tint);
    }

    /** Full upsert including the per-profile tint switch. */
    public static int upsert(ResourceLocation id, int radius, boolean ignoreConditions, int color,
                             boolean tintEnabled) {
        int clamped = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        Map<ResourceLocation, Profile> merged = new HashMap<>(PROFILES);
        merged.put(id, new Profile(clamped, ignoreConditions, color & 0xFFFFFF, tintEnabled));
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

    /**
     * Emitter tint for a state, or -1 when none applies (vanilla color).
     * A profile with the tint switch off ({@code light_color_enabled}) keeps
     * its extended radius but renders fully default — vanilla light, no hue.
     */
    public static int colorForState(BlockState state) {
        if (!enabled) {
            return -1;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Profile p = id == null ? null : get(id);
        LightEngineApi.ColorProvider provider = id == null ? null : COLOR_PROVIDERS.get(id);
        if (provider != null && (p == null || PROFILES.get(id) == null)) {
            // Mod-defined color, unless the user configured this block.
            if (p != null && !p.tintEnabled()) {
                return -1;
            }
            int c;
            try {
                c = provider.getColor(state);
            } catch (RuntimeException ex) {
                return -1;
            }
            return c < 0 ? -1 : c & 0xFFFFFF;
        }
        if (p == null) {
            return -1;
        }
        if (!p.tintEnabled()) {
            return -1;
        }
        if (!p.ignoreConditions()) {
            if (state.hasProperty(BlockStateProperties.LIT)
                    && !Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT))) {
                return -1;
            }
            if (state.hasProperty(BlockStateProperties.POWERED)
                    && !Boolean.TRUE.equals(state.getValue(BlockStateProperties.POWERED))) {
                return -1;
            }
        }
        return p.color();
    }

    /** Parses `#RRGGBB`, `RRGGBB` or a number; returns DEFAULT_COLOR on failure. */
    public static int parseColor(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue() & 0xFFFFFF;
        }
        if (raw instanceof String s) {
            String t = s.strip();
            if (t.startsWith("#")) {
                t = t.substring(1);
            }
            if (t.startsWith("0x") || t.startsWith("0X")) {
                t = t.substring(2);
            }
            if (t.length() == 6) {
                try {
                    return Integer.parseUnsignedInt(t, 16) & 0xFFFFFF;
                } catch (NumberFormatException ignored) {
                }
            }
            if (t.length() == 3) {
                try {
                    int r = Integer.parseUnsignedInt(t.substring(0, 1), 16);
                    int g = Integer.parseUnsignedInt(t.substring(1, 2), 16);
                    int b = Integer.parseUnsignedInt(t.substring(2, 3), 16);
                    r = (r << 4) | r;
                    g = (g << 4) | g;
                    b = (b << 4) | b;
                    return (r << 16) | (g << 8) | b;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return DEFAULT_COLOR;
    }

    public static String formatColor(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
