package dev.cesium.launcher.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.cesium.launcher.util.LauncherException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Loopback HTTP server that receives the OAuth redirect and hands the authorization code back. */
public final class OAuthCallbackServer implements AutoCloseable {
    public static final int FIRST_PORT = 49152;
    public static final int LAST_PORT = 49162;

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><title>Cesium - 1.21.11</title>
            <style>
            html,body{height:100%%;margin:0}
            body{background:#0A0A0A;color:#fff;font-family:'Bebas Neue','Oswald',Impact,sans-serif;
            display:flex;align-items:center;justify-content:center;text-align:center}
            .card{padding:48px 64px;border-radius:12px;border:1px solid #B6FF00;
            background:linear-gradient(135deg,#0A0A0A 0%%,#16200a 100%%);box-shadow:0 0 40px rgba(182,255,0,.25)}
            h1{margin:0 0 12px;font-size:56px;letter-spacing:2px;color:#B6FF00}
            p{margin:0;font-family:Arial,sans-serif;font-size:16px;opacity:.85}
            </style></head><body><div class="card"><h1>%s</h1><p>%s</p></div></body></html>
            """;

    private final HttpServer server;
    private final int port;
    private final CompletableFuture<String> code = new CompletableFuture<>();

    private OAuthCallbackServer(HttpServer server, int port) {
        this.server = server;
        this.port = port;
    }

    /** Binds the first free port in 49152-49162 and starts serving /callback. */
    public static OAuthCallbackServer start(String expectedState) throws LauncherException {
        IOException last = null;
        for (int p = FIRST_PORT; p <= LAST_PORT; p++) {
            try {
                HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), p), 0);
                OAuthCallbackServer wrapper = new OAuthCallbackServer(s, p);
                s.createContext("/callback", ex -> wrapper.handle(ex, expectedState));
                s.start();
                return wrapper;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new LauncherException("Could not open a local port (" + FIRST_PORT + "-" + LAST_PORT
                + ") for the Ely.by login callback" + (last == null ? "." : ": " + last.getMessage()), last);
    }

    public int port() {
        return port;
    }

    public String redirectUri() {
        return "http://127.0.0.1:" + port + "/callback";
    }

    /** Completes with the authorization code, exceptionally on error, or cancelled by {@link #close()}. */
    public CompletableFuture<String> codeFuture() {
        return code;
    }

    private void handle(HttpExchange ex, String expectedState) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        String error = q.get("error");
        String receivedState = q.get("state");
        String authCode = q.get("code");

        int status = 200;
        String title = "You can close this tab";
        String text = "Cesium received your Ely.by login. Return to the launcher.";
        Throwable failure = null;

        if (error != null) {
            status = 400;
            title = "Login failed";
            text = "Ely.by reported: " + escape(error);
            failure = new LauncherException("Ely.by login was refused: " + error
                    + (q.containsKey("error_description") ? " (" + q.get("error_description") + ")" : ""));
        } else if (expectedState == null || !expectedState.equals(receivedState)) {
            status = 400;
            title = "Login failed";
            text = "The login state did not match. Start the login again from the launcher.";
            failure = new LauncherException("Ely.by login state mismatch; possible forged callback.");
        } else if (authCode == null || authCode.isBlank()) {
            status = 400;
            title = "Login failed";
            text = "No authorization code was received.";
            failure = new LauncherException("Ely.by did not return an authorization code.");
        }

        byte[] body = String.format(PAGE, escape(title), text).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
        ex.close();

        if (failure != null) {
            code.completeExceptionally(failure);
        } else {
            code.complete(authCode);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Stops the server and cancels any waiter on {@link #codeFuture()}. */
    @Override
    public void close() {
        code.cancel(true);
        server.stop(0);
    }
}
