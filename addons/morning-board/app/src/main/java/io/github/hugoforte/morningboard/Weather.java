package io.github.hugoforte.morningboard;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * The temperature now and today's low and high, from Open-Meteo (no key, as the Weather add-on uses), for the
 * routine's weather block. The board asks at most every REFRESH_MS and shows the last answer.
 */
final class Weather {
    static final long REFRESH_MS = 15 * 60_000L;

    final int now;
    final int low;
    final int high;

    Weather(int now, int low, int high) {
        this.now = now;
        this.low = low;
        this.high = high;
    }

    /** "54° · ↓42° ↑66°": now, then today's low and high, in the unit asked for. */
    String line() {
        return now + "°  ·  ↓" + low + "°  ↑" + high + "°";
    }

    static String url(Routine.Place place) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m&daily=temperature_2m_min,temperature_2m_max&timezone=auto"
                        + "&forecast_days=1&temperature_unit=%s",
                place.latitude, place.longitude, place.fahrenheit ? "fahrenheit" : "celsius");
    }

    /** Reads Open-Meteo's answer to url(). */
    static Weather parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject daily = o.getJSONObject("daily");
            return new Weather((int) Math.round(o.getJSONObject("current").getDouble("temperature_2m")),
                    (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                    (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Open-Meteo's answer could not be read: " + e.getMessage(), e);
        }
    }

    static Weather fetch(Routine.Place place) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(place)).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("Open-Meteo answered " + code);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
        }
        return parse(bytes.toString("UTF-8"));
    }
}
