package me.tewek.lightengine.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tewek.lightengine.LightEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * GitHub release checker + downloader. All network runs on background
 * threads; the UI polls {@link #getState()} (e.g. from screen tick).
 * Downloaded jars land in the {@code mods} folder and the currently loaded
 * mod jar is renamed to {@code .dis} so versions never clash.
 */
public final class UpdateChecker {
    private UpdateChecker() {
    }

    public enum State {
        IDLE,
        CHECKING,
        AVAILABLE,
        UPTODATE,
        DOWNLOADING,
        DONE,
        FAILED
    }

    private static final String API_URL = "https://api.github.com/repos/tewek444/Light-Engine/releases/latest";

    private static volatile State state = State.IDLE;
    private static volatile String remoteVersion = "";
    private static volatile String changelog = "";
    private static volatile String assetName = "";
    private static volatile String assetUrl = "";
    private static volatile long assetSize;
    private static volatile long downloaded;
    private static volatile String error = "";

    public static State getState() {
        return state;
    }

    public static String remoteVersion() {
        return remoteVersion;
    }

    public static String changelog() {
        return changelog;
    }

    public static String assetName() {
        return assetName;
    }

    public static long assetSize() {
        return assetSize;
    }

    public static long downloaded() {
        return downloaded;
    }

    public static String error() {
        return error;
    }

    public static double fraction() {
        long total = assetSize;
        if (total <= 0) {
            return -1.0;
        }
        return Math.clamp((double) downloaded / (double) total, 0.0, 1.0);
    }

    private static String norm(String version) {
        if (version == null) {
            return "";
        }
        String v = version.trim().toLowerCase(Locale.ROOT);
        while (v.startsWith("v")) {
            v = v.substring(1);
        }
        return v;
    }

    private static void fail(String message) {
        error = message == null ? "" : message;
        state = State.FAILED;
    }

    public static void check(String currentVersion, String loaderToken) {
        if (state == State.CHECKING || state == State.DOWNLOADING) {
            return;
        }
        state = State.CHECKING;
        error = "";
        remoteVersion = "";
        changelog = "";
        assetName = "";
        assetUrl = "";
        assetSize = 0;
        downloaded = 0;
        Thread thread = new Thread(() -> checkNow(currentVersion, loaderToken), "lightengine-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkNow(String currentVersion, String loaderToken) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .header("User-Agent", "LightEngine")
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                fail(Component.translatable("lightengine.editor.no_releases").getString());
                return;
            }
            if (response.statusCode() != 200) {
                fail(Component.translatable("lightengine.editor.check_failed", "HTTP " + response.statusCode()).getString());
                return;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String tag = root.has("tag_name") && !root.get("tag_name").isJsonNull()
                    ? root.get("tag_name").getAsString() : "";
            String body = root.has("body") && !root.get("body").isJsonNull()
                    ? root.get("body").getAsString() : "";
            if (norm(tag).equals(norm(currentVersion))) {
                state = State.UPTODATE;
                return;
            }
            String token = loaderToken == null ? "" : loaderToken.toLowerCase(Locale.ROOT);
            String pickName = "";
            String pickUrl = "";
            long pickSize = 0;
            String firstJarName = "";
            String firstJarUrl = "";
            long firstJarSize = 0;
            if (root.has("assets") && root.get("assets").isJsonArray()) {
                for (var element : root.getAsJsonArray("assets")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject asset = element.getAsJsonObject();
                    String name = asset.has("name") && !asset.get("name").isJsonNull()
                            ? asset.get("name").getAsString() : "";
                    String url = asset.has("browser_download_url") && !asset.get("browser_download_url").isJsonNull()
                            ? asset.get("browser_download_url").getAsString() : "";
                    long size = asset.has("size") && !asset.get("size").isJsonNull()
                            ? asset.get("size").getAsLong() : 0;
                    if (!name.endsWith(".jar") || url.isEmpty()) {
                        continue;
                    }
                    if (firstJarUrl.isEmpty()) {
                        firstJarName = name;
                        firstJarUrl = url;
                        firstJarSize = size;
                    }
                    if (!token.isEmpty() && name.toLowerCase(Locale.ROOT).contains(token)) {
                        pickName = name;
                        pickUrl = url;
                        pickSize = size;
                        break;
                    }
                }
            }
            if (pickUrl.isEmpty()) {
                pickName = firstJarName;
                pickUrl = firstJarUrl;
                pickSize = firstJarSize;
            }
            if (pickUrl.isEmpty()) {
                fail(Component.translatable("lightengine.editor.no_assets").getString());
                return;
            }
            remoteVersion = tag;
            changelog = body;
            assetName = pickName;
            assetUrl = pickUrl;
            assetSize = pickSize;
            state = State.AVAILABLE;
        } catch (Exception ex) {
            String message = ex.getMessage();
            fail(Component.translatable("lightengine.editor.check_failed",
                    ex.getClass().getSimpleName() + (message == null ? "" : ": " + message)).getString());
        }
    }

    public static void download() {
        if (state != State.AVAILABLE || assetUrl.isEmpty()) {
            return;
        }
        state = State.DOWNLOADING;
        downloaded = 0;
        Thread thread = new Thread(UpdateChecker::downloadNow, "lightengine-update-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static void downloadNow() {
        Path tmp = null;
        try {
            Path modsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
            Files.createDirectories(modsDir);
            tmp = modsDir.resolve(assetName + ".part");
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(assetUrl))
                    .header("User-Agent", "LightEngine")
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                fail(Component.translatable("lightengine.editor.check_failed", "HTTP " + response.statusCode()).getString());
                return;
            }
            response.headers().firstValueAsLong("Content-Length").ifPresent(len -> {
                if (len > 0) {
                    assetSize = len;
                }
            });
            try (InputStream in = response.body();
                 var out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int read;
                while ((read = in.read(buf)) >= 0) {
                    out.write(buf, 0, read);
                    downloaded += read;
                }
            }
            Path target = modsDir.resolve(assetName);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            disableCurrentJar(target);
            state = State.DONE;
        } catch (Exception ex) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
            String message = ex.getMessage();
            fail(Component.translatable("lightengine.editor.check_failed",
                    ex.getClass().getSimpleName() + (message == null ? "" : ": " + message)).getString());
        }
    }

    private static void disableCurrentJar(Path downloaded) {
        try {
            String location = LightEngine.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (location == null || !location.endsWith(".jar")) {
                return;
            }
            Path current = Path.of(location);
            if (current.equals(downloaded) || !Files.isRegularFile(current)) {
                return;
            }
            Files.move(current, current.resolveSibling(current.getFileName() + ".dis"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not disable old mod jar; remove it manually before restart", ex);
        }
    }

    public static boolean restart() {
        try {
            ProcessHandle self = ProcessHandle.current();
            String command = self.info().command().orElse(null);
            if (command == null || command.isBlank()) {
                return false;
            }
            List<String> full = new ArrayList<>();
            full.add(command);
            self.info().arguments().ifPresent(args -> full.addAll(Arrays.asList(args)));
            new ProcessBuilder(full).directory(new File(".").getAbsoluteFile()).start();
            Minecraft.getInstance().stop();
            return true;
        } catch (Exception ex) {
            error = ex.getClass().getSimpleName();
            return false;
        }
    }
}
