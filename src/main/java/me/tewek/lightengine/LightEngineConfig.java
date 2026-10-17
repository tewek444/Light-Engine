package me.tewek.lightengine;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric owner of {@code lightengine-common.toml}: loads it at startup (fresh
 * installs get the default preset via {@link Config#refresh}), and
 * hot-reloads it through a config-dir watcher, like NeoForge's config system.
 */
public final class LightEngineConfig {
    private LightEngineConfig() {
    }

    private static final Object LOCK = new Object();
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);

    public static void init() {
        reloadFromDisk();
        startWatcher();
    }

    private static void reloadFromDisk() {
        synchronized (LOCK) {
            Path path = ConfigFile.path();
            try {
                Files.createDirectories(path.getParent());
            } catch (Exception ignored) {
                // load below reports the real problem
            }
            CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
            try {
                file.load();
                if (Config.refresh(file)) {
                    file.save();
                }
                Config.ensureExampleComment();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not load {}", ConfigFile.FILE_NAME, ex);
            } finally {
                try {
                    file.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }

    private static void startWatcher() {
        if (!WATCHING.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(LightEngineConfig::watchLoop, "lightengine-config-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static void watchLoop() {
        try {
            Path dir = ConfigFile.path().getParent();
            try (WatchService watcher = dir.getFileSystem().newWatchService()) {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey key = watcher.take();
                    boolean ours = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context() instanceof Path name && name.toString().equals(ConfigFile.FILE_NAME)) {
                            ours = true;
                        }
                    }
                    key.reset();
                    if (ours) {
                        // Debounce: editors and our own saves fire several events.
                        Thread.sleep(500);
                        reloadFromDisk();
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Config watcher stopped", ex);
        }
    }
}
