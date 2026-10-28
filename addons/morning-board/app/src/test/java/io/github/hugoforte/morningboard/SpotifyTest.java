package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SpotifyTest {
    private static final String DEVICES = "{\"devices\": ["
            + "{\"id\": \"speaker-1\", \"name\": \"Livingroom\", \"type\": \"Speaker\"},"
            + "{\"id\": \"tv-1\", \"name\": \"Living Room TV\", \"type\": \"TV\"}]}";

    @Test
    public void theDeviceIsFoundByItsName() {
        assertEquals("tv-1", Spotify.deviceId(DEVICES, "Living Room TV"));
    }

    @Test
    public void aDeviceThatIsNotThereYetIsNull() {
        assertNull(Spotify.deviceId("{\"devices\": []}", "Living Room TV"));
    }

    @Test
    public void aSimilarNameIsNotTheDevice() {
        assertNull(Spotify.deviceId(DEVICES, "Living Room"));
    }

    @Test
    public void spotifyJsonIsRead() {
        assertEquals("refresh", Spotify.Account.parse(
                "{\"clientId\": \"id\", \"clientSecret\": \"secret\", \"refreshToken\": \"refresh\"}").refreshToken);
    }

    @Test
    public void spotifyJsonWithAnEmptyTokenIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Spotify.Account.parse(
                "{\"clientId\": \"id\", \"clientSecret\": \"secret\", \"refreshToken\": \"\"}"));
    }
}
