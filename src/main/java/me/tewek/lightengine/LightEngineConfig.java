package me.tewek.lightengine;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owner of the single {@code lightengine-common.toml} file.
 * Loads it at startup, keeps profile tables intact on every write,
 * and hot-reloads it through a config-dir watcher.
 */
public final class LightEngineConfig {
    private LightEngineConfig() {
    }

    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_CNFLVL = "1.0.0";
    public static final String FILE_NAME = "lightengine-common.toml";

    private static final Object LOCK = new Object();
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);

    public static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** Initial load: creates the file with defaults when missing. */
    public static void init() {
        synchronized (LOCK) {
            CommentedFileConfig file = open();
            try {
                file.load();
                if (Config.refresh(file)) {
                    file.save();
                }
                Config.ensureExampleComment();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not load {}", FILE_NAME, ex);
            } finally {
                closeQuietly(file);
            }
        }
        startWatcher();
    }

    /**
     * Writes {@code enabled}/{@code cnflvl} (used by the settings screen).
     * Loads and saves the raw file, so profile tables and comments survive.
     */
    public static void saveManual(boolean enabled, String cnflvl) {
        synchronized (LOCK) {
            CommentedFileConfig file = open();
            try {
                file.load();
                file.set("enabled", enabled);
                file.set("cnflvl", cnflvl == null || cnflvl.isBlank() ? DEFAULT_CNFLVL : cnflvl);
                Config.refresh(file);
                file.save();
                Config.ensureExampleComment();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not save {}", FILE_NAME, ex);
            } finally {
                closeQuietly(file);
            }
        }
    }

    private static void reloadFromDisk() {
        synchronized (LOCK) {
            CommentedFileConfig file = open();
            try {
                file.load();
                if (Config.refresh(file)) {
                    file.save();
                }
                Config.ensureExampleComment();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not reload {}", FILE_NAME, ex);
            } finally {
                closeQuietly(file);
            }
        }
    }

    private static CommentedFileConfig open() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception ignored) {
        }
        return CommentedFileConfig.builder(path).sync().build();
    }

    private static void closeQuietly(CommentedFileConfig file) {
        try {
            file.close();
        } catch (Exception ignored) {
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
            Path dir = filePath().getParent();
            try (WatchService watcher = dir.getFileSystem().newWatchService()) {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey key;
                    try {
                        key = watcher.take();
                    } catch (InterruptedException ex) {
                        return;
                    }
                    boolean ours = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Object ctx = event.context();
                        if (ctx instanceof Path name && name.toString().equals(FILE_NAME)) {
                            ours = true;
                        }
                    }
                    key.reset();
                    if (!ours) {
                        continue;
                    }
                    // Debounce: editors and our own saves may fire several events.
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        return;
                    }
                    reloadFromDisk();
                }
            }
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Config watcher stopped", ex);
        }
    }
}
