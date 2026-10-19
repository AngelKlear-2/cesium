package dev.cesium.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Thin wrapper around java.net.http.HttpClient with the launcher's user-agent, retries and downloads. */
public final class HttpService {
    public static final String USER_AGENT = "Cesium-Launcher/1.0";
    private static final int DOWNLOAD_ATTEMPTS = 3;

    private final HttpClient client;

    public HttpService() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String getString(String url) throws LauncherException {
        return getString(url, Map.of());
    }

    public String getString(String url, Map<String, String> headers) throws LauncherException {
        HttpRequest.Builder b = request(url).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    public String postForm(String url, Map<String, String> fields) throws LauncherException {
        String body = fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest req = request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(req);
    }

    private HttpRequest.Builder request(String url) {
        try {
            return HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    private String send(HttpRequest req) throws LauncherException {
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                String body = resp.body() == null ? "" : resp.body();
                if (body.length() > 300) {
                    body = body.substring(0, 300);
                }
                throw new LauncherException("Server answered HTTP " + code + " for " + req.uri().getHost()
                        + (body.isBlank() ? "" : ": " + body.strip()));
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LauncherException("Request cancelled.", e);
        } catch (IOException e) {
            throw new LauncherException("Network problem contacting " + req.uri().getHost() + ": "
                    + describe(e), e);
        }
    }

    /**
     * Downloads url to target through a .part file. An existing target with a matching hash is kept.
     * Retries up to three times on I/O errors or hash mismatch.
     */
    public void download(String url, Path target, String expectedSha1) throws LauncherException {
        if (Hashes.isValid(target, expectedSha1)) {
            return;
        }
        LauncherException last = null;
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadOnce(url, target, expectedSha1);
                return;
            } catch (LauncherException e) {
                last = e;
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
        throw last;
    }

    private void downloadOnce(String url, Path target, String expectedSha1) throws LauncherException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            HttpRequest req = request(url).timeout(Duration.ofMinutes(5)).GET().build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                resp.body().close();
                throw new LauncherException("HTTP " + resp.statusCode() + " while downloading " + url);
            }
            MessageDigest digest = Hashes.newSha1();
            try (InputStream in = resp.body(); var out = Files.newOutputStream(part)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                }
            }
            if (expectedSha1 != null && !expectedSha1.isBlank()
                    && !Hashes.hex(digest.digest()).equalsIgnoreCase(expectedSha1)) {
                throw new LauncherException("Checksum mismatch for " + target.getFileName());
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LauncherException("Download cancelled.", e);
        } catch (IOException e) {
            throw new LauncherException("Could not download " + url + ": " + describe(e), e);
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // A leftover .part file is harmless and overwritten on the next attempt.
            }
        }
    }

    public static String describe(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
