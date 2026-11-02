package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small, inspectable Gradle bootstrap used solely because the official wrapper
 * JAR could not be included in this generated project. It downloads the
 * distribution declared in gradle-wrapper.properties and invokes its launcher.
 * For the official Gradle wrapper, run `gradle wrapper --gradle-version 8.13`.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() { }

    public static void main(String[] args) throws Exception {
        CodeSource source = GradleWrapperMain.class.getProtectionDomain().getCodeSource();
        Path jar = Path.of(source.getLocation().toURI()).toAbsolutePath();
        Path gradleDir = jar.getParent().toAbsolutePath();
        Path projectDir = gradleDir.getParent().getParent();
        Path config = gradleDir.resolve("gradle-wrapper.properties");

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(config)) {
            properties.load(stream);
        }
        URI url = URI.create(properties.getProperty("distributionUrl"));
        if (!"https".equalsIgnoreCase(url.getScheme()) ||
            !"services.gradle.org".equalsIgnoreCase(url.getHost())) {
            throw new IOException("Only official Gradle HTTPS distribution URLs are accepted");
        }
        String zipName = Path.of(url.getPath()).getFileName().toString();
        if (!zipName.endsWith("-bin.zip")) {
            throw new IOException("Expected a Gradle binary distribution URL");
        }
        String distributionName = zipName.substring(0, zipName.length() - "-bin.zip".length());
        Path userHome = Path.of(System.getProperty("user.home"), ".gradle");
        String specifiedHome = System.getenv("GRADLE_USER_HOME");
        if (specifiedHome != null && !specifiedHome.isBlank()) {
            userHome = Path.of(specifiedHome);
        }
        Path cache = userHome.resolve("wrapper").resolve("dists")
            .resolve("staroeradio-bootstrap").resolve(distributionName);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path binary = cache.resolve(distributionName).resolve("bin")
            .resolve(windows ? "gradle.bat" : "gradle");

        if (!Files.isRegularFile(binary)) {
            Files.createDirectories(cache);
            Path tempZip = Files.createTempFile(cache, "gradle-", ".zip");
            Path extractDir = Files.createTempDirectory(cache, "extract-");
            try {
                System.out.println("Downloading official Gradle from " + url + " ...");
                download(url, tempZip);
                extract(tempZip, extractDir);
                Path extractedGradle = extractDir.resolve(distributionName);
                if (!Files.isRegularFile(extractedGradle.resolve("bin")
                    .resolve(windows ? "gradle.bat" : "gradle"))) {
                    throw new IOException("Unexpected Gradle ZIP layout");
                }
                Path destination = cache.resolve(distributionName);
                if (!Files.isRegularFile(binary)) {
                    if (Files.exists(destination)) deleteTree(destination);
                    Files.move(extractedGradle, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                binary.toFile().setExecutable(true, false);
            } finally {
                Files.deleteIfExists(tempZip);
                deleteTree(extractDir);
            }
        }

        List<String> command = new ArrayList<>();
        if (windows) {
            command.addAll(Arrays.asList("cmd.exe", "/c", binary.toString()));
        } else {
            command.add(binary.toString());
        }
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
            .directory(projectDir.toFile())
            .inheritIO()
            .start();
        System.exit(process.waitFor());
    }

    private static void download(URI url, Path target) throws IOException {
        java.net.URL current = url.toURL();
        for (int redirect = 0; redirect < 8; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Redirect without Location header");
                current = URI.create(current.toString()).resolve(location).toURL();
                if (!"https".equalsIgnoreCase(current.getProtocol())) {
                    throw new IOException("Gradle redirect must use HTTPS");
                }
                continue;
            }
            if (status != 200) {
                throw new IOException("Gradle download failed: HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(target)) {
                input.transferTo(output);
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IOException("Too many Gradle distribution redirects");
    }

    private static void extract(Path zip, Path destination) throws IOException {
        try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) throw new IOException("Unsafe ZIP entry");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(stream, output, StandardCopyOption.REPLACE_EXISTING);
                }
                stream.closeEntry();
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
