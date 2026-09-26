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
    /** Staged swap: set after a successful download. */
    private static volatile Path previousJar;
    private static volatile Path downloadedJar;

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

    /**
     * Version used for the "latest?" comparison. Overridable with
     * {@code -Dlightengine.version=...} to test the update flow without
     * cutting a release (an older value makes the current release look new).
     */
    public static String effectiveVersion() {
        String override = System.getProperty("lightengine.version", "");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return LightEngine.VERSION;
    }

    public static void checkCurrent(String loaderToken) {
        check(effectiveVersion(), loaderToken);
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

    /** SemVer-ish comparison: build metadata ignored, release beats prerelease. */
    static int compareVersions(String a, String b) {
        String[] pa = stripBuild(a).split("-", 2);
        String[] pb = stripBuild(b).split("-", 2);
        int core = compareCore(pa[0], pb[0]);
        if (core != 0) {
            return core;
        }
        boolean aPre = pa.length > 1;
        boolean bPre = pb.length > 1;
        if (!aPre && !bPre) {
            return 0;
        }
        if (!aPre) {
            return 1;
        }
        if (!bPre) {
            return -1;
        }
        return comparePrerelease(pa[1], pb[1]);
    }

    private static String stripBuild(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static int compareCore(String a, String b) {
        String[] xa = a.split("\\.");
        String[] xb = b.split("\\.");
        int n = Math.max(xa.length, xb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < xa.length ? xa[i] : "0";
            String sb = i < xb.length ? xb[i] : "0";
            int cmp = comparePart(sa, sb);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int comparePrerelease(String a, String b) {
        String[] xa = a.split("\\.");
        String[] xb = b.split("\\.");
        int n = Math.max(xa.length, xb.length);
        for (int i = 0; i < n; i++) {
            if (i >= xa.length) {
                return -1;
            }
            if (i >= xb.length) {
                return 1;
            }
            int cmp = comparePart(xa[i], xb[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int comparePart(String a, String b) {
        Integer na = tryParse(a);
        Integer nb = tryParse(b);
        if (na != null && nb != null) {
            return Integer.compare(na, nb);
        }
        if (na != null) {
            return -1;
        }
        if (nb != null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private static Integer tryParse(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
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
            remoteVersion = tag;
            changelog = body;
            if (compareVersions(norm(tag), norm(currentVersion)) <= 0) {
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
            downloadedJar = target;
            previousJar = currentJarPath();
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

    private static Path currentJarPath() {
        try {
            Path current = Path.of(LightEngine.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(current) ? current : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void disableCurrentJar(Path downloaded) {
        try {
            Path current = currentJarPath();
            if (current == null || current.equals(downloaded)) {
                return;
            }
            // Best effort: works on Linux, locked on Windows (the swap script
            // below handles that case after the process exits).
            Files.move(current, current.resolveSibling(current.getFileName() + ".dis"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not disable old mod jar; the restart swap will handle it", ex);
        }
    }

    private static List<String> launchCommand() {
        try {
            ProcessHandle self = ProcessHandle.current();
            String command = self.info().command().orElse(null);
            if (command == null || command.isBlank()) {
                return null;
            }
            List<String> full = new ArrayList<>();
            full.add(command);
            self.info().arguments().ifPresent(args -> full.addAll(Arrays.asList(args)));
            return full;
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean restart() {
        List<String> full = launchCommand();
        if (full == null || full.isEmpty()) {
            return false;
        }
        try {
            new ProcessBuilder(full).directory(new File(".").getAbsoluteFile()).start();
            Minecraft.getInstance().stop();
            return true;
        } catch (Exception ex) {
            error = ex.getClass().getSimpleName();
            return false;
        }
    }

    /**
     * Restart through a swap script: it waits for this process to exit, then
     * disables the old jar ({@code .dis}) and starts the game with the same
     * command line. Required on Windows, where the loaded jar is file-locked
     * and cannot be renamed from inside the game. Falls back to a plain
     * relaunch when nothing is staged.
     */
    public static boolean restartToApplyUpdate() {
        Path next = downloadedJar;
        if (next == null || !Files.isRegularFile(next)) {
            return restart();
        }
        Path prev = previousJar;
        List<String> cmd = launchCommand();
        if (cmd == null || cmd.isEmpty()) {
            rollbackDownload(next);
            return false;
        }
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath();
            long pid = ProcessHandle.current().pid();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path script = gameDir.resolve(windows ? "lightengine-update.bat" : "lightengine-update.sh");
            String content = windows
                    ? batScript(pid, gameDir, prev, cmd)
                    : shScript(pid, gameDir, prev, cmd);
            Files.writeString(script, content, java.nio.charset.StandardCharsets.UTF_8);
            ProcessBuilder pb = windows
                    ? new ProcessBuilder("cmd.exe", "/c", script.toString())
                    : new ProcessBuilder("/bin/sh", script.toString());
            pb.directory(gameDir.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            downloadedJar = null;
            previousJar = null;
            Minecraft.getInstance().stop();
            return true;
        } catch (Exception ex) {
            error = ex.getClass().getSimpleName();
            rollbackDownload(next);
            return false;
        }
    }

    private static void rollbackDownload(Path next) {
        downloadedJar = null;
        previousJar = null;
        try {
            Files.deleteIfExists(next);
        } catch (Exception ignored) {
        }
    }

    private static String batQuote(String s) {
        if (s.isEmpty() || s.contains(" ") || s.contains("\"") || s.contains("&")
                || s.contains("(") || s.contains(")")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String batScript(long pid, Path gameDir, Path prev, List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append("rem LightEngine self-update driver\r\n");
        sb.append(":le_wait\r\n");
        sb.append("tasklist /FI \"PID eq ").append(pid).append("\" 2>nul | find \"")
                .append(pid).append("\" >nul\r\n");
        sb.append("if %errorlevel%==0 (\r\n  timeout /t 1 /nobreak >nul\r\n  goto le_wait\r\n)\r\n");
        if (prev != null) {
            sb.append("if exist ").append(batQuote(prev.toString())).append(" move /Y ")
                    .append(batQuote(prev.toString())).append(' ')
                    .append(batQuote(prev.toString() + ".dis")).append("\r\n");
        }
        sb.append("start \"\" /D ").append(batQuote(gameDir.toString()));
        for (String token : cmd) {
            sb.append(' ').append(batQuote(token));
        }
        sb.append("\r\n");
        sb.append("(goto) 2>nul & del \"%~f0\"\r\n");
        return sb.toString();
    }

    private static String shScript(long pid, Path gameDir, Path prev, List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("LE_PID=").append(pid).append('\n');
        sb.append("while kill -0 \"$LE_PID\" 2>/dev/null; do sleep 1; done\n");
        if (prev != null) {
            sb.append("mv -f ").append(shQuote(prev.toString())).append(' ')
                    .append(shQuote(prev.toString() + ".dis")).append(" || true\n");
        }
        sb.append("rm -- \"$0\"\n");
        sb.append("cd ").append(shQuote(gameDir.toString())).append(" || exit 1\n");
        sb.append("exec");
        for (String token : cmd) {
            sb.append(' ').append(shQuote(token));
        }
        sb.append('\n');
        return sb.toString();
    }
}
