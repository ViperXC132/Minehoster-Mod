package dev.minehoster;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerDownloader {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private ServerDownloader() {}

    public static void downloadVanilla(Path serverDir, Progress progress) throws Exception {
        Files.createDirectories(serverDir);
        progress.message("Finding Vanilla 1.21.1 server jar...");
        String manifest = get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        JsonArray versions = JsonParser.parseString(manifest).getAsJsonObject().getAsJsonArray("versions");
        String versionUrl = null;
        for (var e : versions) {
            JsonObject v = e.getAsJsonObject();
            if ("1.21.1".equals(v.get("id").getAsString())) { versionUrl = v.get("url").getAsString(); break; }
        }
        if (versionUrl == null) throw new IOException("Minecraft 1.21.1 metadata not found");
        JsonObject downloads = JsonParser.parseString(get(versionUrl)).getAsJsonObject().getAsJsonObject("downloads");
        String jarUrl = downloads.getAsJsonObject("server").get("url").getAsString();
        download(jarUrl, serverDir.resolve("server.jar"), progress);
    }

    public static void downloadPaper(Path serverDir, Progress progress) throws Exception {
        Files.createDirectories(serverDir);
        progress.message("Finding latest Paper 1.21.1 build...");
        JsonObject project = JsonParser.parseString(get("https://api.papermc.io/v2/projects/paper/1.21.1")).getAsJsonObject();
        JsonArray builds = project.getAsJsonArray("builds");
        if (builds.isEmpty()) throw new IOException("No Paper builds found");
        int build = builds.get(builds.size() - 1).getAsInt();
        String url = "https://api.papermc.io/v2/projects/paper/1.21.1/" + build + "/downloads/paper-1.21.1-" + build + ".jar";
        download(url, serverDir.resolve("server.jar"), progress);
    }

    public static void downloadPlugin(Path serverDir, String url, Progress progress) throws Exception {
        if (url == null || !url.toLowerCase().endsWith(".jar")) throw new IOException("Plugin URL must point to a .jar file");
        Path plugins = serverDir.resolve("plugins").normalize();
        Files.createDirectories(plugins);
        String file = URI.create(url).getPath();
        file = file.substring(file.lastIndexOf('/') + 1);
        if (file.isBlank()) file = "plugin.jar";
        if (!file.endsWith(".jar")) file += ".jar";
        download(url, plugins.resolve(file), progress);
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Minehoster/1.0").GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " from " + url);
        return r.body();
    }

    private static void download(String url, Path destination, Progress progress) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Minehoster/1.0").GET().build();
        HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " downloading " + url);
        Path tmp = destination.resolveSibling(destination.getFileName() + ".part");
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[16384]; long total = 0; int n;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); total += n; if ((total & ((1 << 20) - 1)) < n) progress.message("Downloading... " + (total / 1024 / 1024) + " MiB"); }
        }
        Files.move(tmp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        progress.message("Downloaded " + destination.getFileName());
    }

    @FunctionalInterface public interface Progress { void message(String message); }
}
