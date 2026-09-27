package me.tewek.lightengine.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Smart search helpers: case/yo normalization, RU&lt;-&gt;EN keyboard layout
 * swapping, and matching against id + current-language + English names.
 */
public final class SearchUtil {
    private SearchUtil() {
    }

    private static final String EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,./";
    private static final String RU_LAYOUT = "йцукенгшщзхъфывапролджэячсмитьбю.";

    private static final Map<Character, Character> SWAP = buildSwap();

    private static volatile Map<String, String> english;

    private static Map<Character, Character> buildSwap() {
        Map<Character, Character> map = new HashMap<>();
        for (int i = 0; i < EN_LAYOUT.length() && i < RU_LAYOUT.length(); i++) {
            map.put(EN_LAYOUT.charAt(i), RU_LAYOUT.charAt(i));
            map.put(RU_LAYOUT.charAt(i), EN_LAYOUT.charAt(i));
        }
        return map;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    public static String swapLayout(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            Character mapped = SWAP.get(c);
            out.append(mapped == null ? c : mapped);
        }
        return out.toString();
    }

    public static boolean matches(String query, String... candidates) {
        String q = normalize(query == null ? "" : query.trim());
        if (q.isEmpty()) {
            return true;
        }
        String swapped = swapLayout(q);
        for (String candidate : candidates) {
            String n = normalize(candidate);
            if (n.contains(q)) {
                return true;
            }
            if (!swapped.equals(q) && n.contains(swapped)) {
                return true;
            }
        }
        return false;
    }

    /** English display name for a translation key (empty when unavailable). */
    public static String englishName(String translationKey) {
        if (translationKey == null) {
            return "";
        }
        return englishNames().getOrDefault(translationKey, "");
    }

    private static Map<String, String> englishNames() {
        Map<String, String> cached = english;
        if (cached != null) {
            return cached;
        }
        Map<String, String> map = new HashMap<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            var resource = mc.getResourceManager()
                    .getResource(ResourceLocation.withDefaultNamespace("lang/en_us.json"));
            if (resource.isPresent()) {
                try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
                    for (var entry : root.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            map.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        english = map;
        return map;
    }
}
