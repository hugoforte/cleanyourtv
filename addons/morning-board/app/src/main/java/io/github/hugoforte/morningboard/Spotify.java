package io.github.hugoforte.morningboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Spotify's Web API, for the morning's music: a playlist started on the TV's Spotify Connect device, and paused
 * again. Spotify's TV app plays a playlist opened by link only on a real press of its Play button, which no app on
 * the TV can make, so the board asks the household's Spotify account instead (Premium, as the API requires).
 */
final class Spotify {
    /** spotify.json: the Spotify developer app's client ID and secret, and the account's refresh token. */
    static final class Account {
        final String clientId;
        final String clientSecret;
        final String refreshToken;

        Account(String clientId, String clientSecret, String refreshToken) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.refreshToken = refreshToken;
        }

        static Account parse(String json) {
            try {
                JSONObject o = new JSONObject(json);
                Account a = new Account(o.getString("clientId").trim(), o.getString("clientSecret").trim(),
                        o.getString("refreshToken").trim());
                if (a.clientId.isEmpty() || a.clientSecret.isEmpty() || a.refreshToken.isEmpty()) {
                    throw new IllegalArgumentException("spotify.json needs a clientId, a clientSecret and a refreshToken");
                }
                return a;
            } catch (JSONException e) {
                throw new IllegalArgumentException("spotify.json: " + e.getMessage(), e);
            }
        }
    }

    private static final String ACCOUNTS = "https://accounts.spotify.com/api/token";
    private static final String API = "https://api.spotify.com/v1";

    private final Account account;
    private String token;

    Spotify(Account account) {
        this.account = account;
    }

    /** The id of the Connect device of that name, or null while it is not there (its app not started yet). */
    String device(String name) throws IOException {
        return deviceId(call("GET", "/me/player/devices", null), name);
    }

    void play(String deviceId, String contextUri) throws IOException {
        call("PUT", "/me/player/play?device_id=" + deviceId, "{\"context_uri\":" + JSONObject.quote(contextUri) + "}");
    }

    void pause() throws IOException {
        call("PUT", "/me/player/pause", null);
    }

    /**
     * Whether a refusal says to ask again soon: a device Spotify does not know yet (404), too many requests (429), or
     * Spotify itself failing (5xx). Not a refusal that would only repeat, like 401 or 403 PREMIUM_REQUIRED.
     */
    static boolean tryLater(IOException refusal) {
        String m = String.valueOf(refusal.getMessage());
        return m.contains("(404)") || m.contains("(429)") || m.matches("(?s).*\\(5\\d\\d\\).*");
    }

    static String deviceId(String devicesJson, String name) {
        try {
            JSONArray devices = new JSONObject(devicesJson).getJSONArray("devices");
            for (int i = 0; i < devices.length(); i++) {
                JSONObject d = devices.getJSONObject(i);
                if (name.equals(d.optString("name"))) return d.getString("id");
            }
            return null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Spotify's device list could not be read: " + e.getMessage(), e);
        }
    }

    private String call(String method, String path, String body) throws IOException {
        if (token == null) token = accessToken();
        HttpURLConnection c = open(API + path, method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null || !method.equals("GET")) {
            c.setRequestProperty("Content-Type", "application/json");
            send(c, body == null ? "" : body);
        }
        return read(c, method + " " + path);
    }

    private String accessToken() throws IOException {
        HttpURLConnection c = open(ACCOUNTS, "POST");
        String basic = Base64.getEncoder().encodeToString(
                (account.clientId + ":" + account.clientSecret).getBytes(StandardCharsets.UTF_8));
        c.setRequestProperty("Authorization", "Basic " + basic);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        send(c, "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(account.refreshToken, "UTF-8"));
        try {
            return new JSONObject(read(c, "the token refresh")).getString("access_token");
        } catch (JSONException e) {
            throw new IOException("Spotify's token answer could not be read", e);
        }
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        return c;
    }

    private static void send(HttpURLConnection c, String body) throws IOException {
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The answer's body; an error status throws with Spotify's own message, e.g. PREMIUM_REQUIRED. */
    private static String read(HttpURLConnection c, String what) throws IOException {
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (in != null) {
            try (InputStream i = in) {
                byte[] buf = new byte[8192];
                for (int n; (n = i.read(buf)) > 0; ) bytes.write(buf, 0, n);
            }
        }
        String body = bytes.toString("UTF-8");
        if (code >= 400) throw new IOException("Spotify refused " + what + " (" + code + "): " + body);
        return body;
    }
}
