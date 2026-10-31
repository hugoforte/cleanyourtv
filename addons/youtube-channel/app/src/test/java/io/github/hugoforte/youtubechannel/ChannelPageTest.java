package io.github.hugoforte.youtubechannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class ChannelPageTest {

    private static final String WATCH = "<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=9TDZetPuv2c\">";

    @Test
    public void aStreamOnAirIsLive() {
        assertEquals("9TDZetPuv2c", ChannelPage.liveVideoId(WATCH + "\"liveBroadcastDetails\":{\"isLiveNow\":true}"));
    }

    @Test
    public void anUpcomingStreamsWaitingRoomIsNotLive() {
        assertNull(ChannelPage.liveVideoId(WATCH + "\"isUpcoming\":true,\"isLive\":true"));
    }

    @Test
    public void theChannelPageIsNotLive() {
        assertNull(ChannelPage.liveVideoId(
                "<link rel=\"canonical\" href=\"https://www.youtube.com/channel/UCPFvsXRt_9InJA9XeBGpRaA\">"));
    }

    @Test
    public void eachLockupIsAShowWithItsTitleAndWhen() {
        String page = "x\"lockupViewModel\":{\"contentId\":\"0VEmzN0qvBc\",\"metadata\":{\"lockupMetadataViewModel\":"
                + "{\"title\":{\"content\":\"Live at Rosa\\u0027s \\\"Lounge\\\"\"},\"metadataRows\":[{\"text\":"
                + "{\"content\":\"2.4K\"}},{\"text\":{\"content\":\"Streamed 7d ago\"}}]}}"
                + "\"lockupViewModel\":{\"contentId\":\"4R9q6tHe8N8\",\"metadata\":{\"lockupMetadataViewModel\":"
                + "{\"title\":{\"content\":\"Second\"}}}";
        List<ChannelPage.Show> shows = ChannelPage.shows(page);
        assertEquals(2, shows.size());
        assertEquals("0VEmzN0qvBc", shows.get(0).videoId);
        assertEquals("Live at Rosa's \"Lounge\"", shows.get(0).title);
        assertEquals("Streamed 7d ago", shows.get(0).when);
        assertEquals("", shows.get(1).when);
    }
}
