package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small, dependency-free Gradle bootstrap used because generated projects need
 * a wrapper before Gradle itself is available. It downloads the pinned Gradle
 * distribution, verifies its SHA-256, extracts it, and forwards all arguments.
 */
public final class GradleWrapperMain {
    private static final int MAX_REDIRECTS = 10;

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path projectDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path propertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }

        String distributionUrl = required(properties, "distributionUrl").replace("\\:", ":");
        String expectedHash = required(properties, "distributionSha256Sum").toLowerCase();
        String archiveName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distributionName = archiveName.replace("-bin.zip", "").replace("-all.zip", "");

        String gradleHomeEnv = System.getenv("GRADLE_USER_HOME");
        Path gradleUserHome = gradleHomeEnv == null || gradleHomeEnv.isBlank()
                ? Path.of(System.getProperty("user.home"), ".gradle")
                : Path.of(gradleHomeEnv);
        Path installRoot = gradleUserHome.resolve("wrapper/dists").resolve(distributionName + "-classquiet");
        Path distributionHome = installRoot.resolve(distributionName);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path gradleExecutable = distributionHome.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");

        if (!Files.isRegularFile(gradleExecutable)) {
            Files.createDirectories(installRoot);
            Path archive = installRoot.resolve(archiveName);
            if (!Files.isRegularFile(archive) || !sha256(archive).equals(expectedHash)) {
                Path partial = installRoot.resolve(archiveName + ".part");
                Files.deleteIfExists(partial);
                System.out.println("Downloading " + distributionUrl);
                download(distributionUrl, partial);
                String actualHash = sha256(partial);
                if (!actualHash.equals(expectedHash)) {
                    Files.deleteIfExists(partial);
                    throw new IOException("Gradle distribution checksum mismatch. Expected "
                            + expectedHash + " but received " + actualHash);
                }
                Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            unzip(archive, installRoot);
        }

        if (!windows) {
            gradleExecutable.toFile().setExecutable(true);
        }

        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(gradleExecutable.toString());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .inheritIO()
                .start();
        System.exit(process.waitFor());
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + key + " in gradle-wrapper.properties");
        }
        return value.trim();
    }

    private static void download(String initialUrl, Path destination) throws Exception {
        URI uri = URI.create(initialUrl);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "ClassQuiet-Gradle-Bootstrap/1.0");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Redirect did not include a Location header");
                uri = uri.resolve(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Gradle download failed with HTTP " + status);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IOException("Too many redirects while downloading Gradle");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination.normalize())) {
                    throw new IOException("Unsafe path in Gradle distribution: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
