package dev.minehoster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JreManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private JreManager() {}

    public static void downloadJava21(Path root, Progress progress) throws Exception {
        Files.createDirectories(root);
        String os = osName();
        String arch = archName();
        String url = "https://api.adoptium.net/v3/binary/latest/21/ga/" + os + "/" + arch + "/jdk/hotspot/normal/eclipse?project=jdk";
        Path archive = root.resolve("temurin-21.download");
        progress.message("Downloading Temurin Java 21...");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) throw new IOException("JRE download failed: HTTP " + response.statusCode());
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(archive)) {
            in.transferTo(out);
        }
        progress.message("Extracting Java 21...");
        Path target = root.resolve("temurin-21");
        if (Files.exists(target)) deleteTree(target);
        Files.createDirectories(target);
        if (os.equals("windows")) extractZip(archive, target);
        else extractTarGz(archive, target);
        Path nested = findJavaHome(target);
        if (nested != null && !nested.equals(target)) {
            Path tmp = root.resolve("temurin-21-flat");
            if (Files.exists(tmp)) deleteTree(tmp);
            Files.move(nested, tmp);
            deleteTree(target);
            Files.move(tmp, target);
        }
        Files.deleteIfExists(archive);
        Path java = findJava(target);
        if (java == null) throw new IOException("Downloaded archive did not contain a Java executable");
        java.toFile().setExecutable(true, false);
        progress.message("Java 21 installed.");
    }

    public static Path findJava(Path root) throws IOException {
        if (!Files.exists(root)) return null;
        try (var walk = Files.walk(root, 5)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(isWindows() ? "java.exe" : "java"))
                    .findFirst().orElse(null);
        }
    }

    private static Path findJavaHome(Path root) throws IOException {
        try (var walk = Files.walk(root, 3)) {
            return walk.filter(Files::isDirectory).filter(p -> Files.isDirectory(p.resolve("bin"))).findFirst().orElse(root);
        }
    }

    private static void extractZip(Path archive, Path target) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) throw new IOException("Unsafe JRE archive");
                if (e.isDirectory()) Files.createDirectories(out); else { Files.createDirectories(out.getParent()); try (OutputStream o = Files.newOutputStream(out)) { zin.transferTo(o); } }
            }
        }
    }

    private static void extractTarGz(Path archive, Path target) throws IOException {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[512];
            while (true) {
                int got = readFully(in, header);
                if (got == 0) break;
                if (got < 512) throw new IOException("Truncated tar archive");
                boolean empty = true; for (byte b : header) if (b != 0) { empty = false; break; }
                if (empty) break;
                String name = new String(header, 0, 100, StandardCharsets.UTF_8).trim();
                long size = parseOctal(header, 124, 12);
                int type = header[156];
                Path out = target.resolve(name).normalize();
                if (!out.startsWith(target)) throw new IOException("Unsafe JRE archive");
                if (type == '5') Files.createDirectories(out);
                else if (type == 0 || type == '0') { Files.createDirectories(out.getParent()); try (OutputStream o = Files.newOutputStream(out)) { copyN(in, o, size); } }
                else copyN(in, OutputStream.nullOutputStream(), size);
                long padding = (512 - (size % 512)) % 512; skipN(in, padding);
            }
        }
    }

    private static int readFully(InputStream in, byte[] b) throws IOException { int off=0,n; while(off<b.length && (n=in.read(b,off,b.length-off))>0) off+=n; return off; }
    private static long parseOctal(byte[] b, int off, int len) { long v=0; for(int i=off;i<off+len && b[i]!=0;i++) if(b[i]>='0'&&b[i]<='7') v=(v<<3)+(b[i]-'0'); return v; }
    private static void copyN(InputStream in, OutputStream out, long n) throws IOException { byte[] b=new byte[8192]; while(n>0){int r=in.read(b,0,(int)Math.min(b.length,n)); if(r<0) throw new IOException("Truncated archive"); out.write(b,0,r); n-=r;} }
    private static void skipN(InputStream in,long n)throws IOException{while(n>0){long s=in.skip(n);if(s<=0){if(in.read()<0)throw new IOException("Truncated archive");s=1;}n-=s;}}
    private static void deleteTree(Path root) throws IOException { if(!Files.exists(root))return; try(var walk=Files.walk(root)){walk.sorted((a,b)->b.compareTo(a)).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});} }
    private static String osName(){String s=System.getProperty("os.name").toLowerCase(); return s.contains("win")?"windows":s.contains("mac")?"mac":"linux";}
    private static String archName(){String s=System.getProperty("os.arch").toLowerCase(); return s.contains("aarch64")||s.contains("arm64")?"aarch64":"x64";}
    private static boolean isWindows(){return osName().equals("windows");}
    @FunctionalInterface public interface Progress { void message(String message); }
}
