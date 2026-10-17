package me.tewek.lightengine;

/**
 * Fabric entrypoint for mods that want to register light profiles without
 * calling {@link LightEngineApi} from their own initializer order. Declare in
 * your {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": { "lightengine": ["com.example.MyLights"] }
 * }</pre>
 * and call {@link LightEngineApi} methods inside {@link #registerLights()}.
 * Runs once on both sides, after Light Engine loaded its config.
 */
@FunctionalInterface
public interface LightEngineEntrypoint {
    void registerLights();
}
