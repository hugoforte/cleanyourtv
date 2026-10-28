package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {
    private static final String ANSWER = "{\"current\": {\"time\": \"2026-10-28T07:15\", \"temperature_2m\": 47.6},"
            + "\"daily\": {\"time\": [\"2026-10-28\"], \"temperature_2m_min\": [41.2], \"temperature_2m_max\": [58.5]}}";

    @Test
    public void theLineGivesNowThenTodaysLowAndHigh() {
        assertEquals("48°  ·  ↓41°  ↑59°", Weather.parse(ANSWER).line());
    }

    @Test
    public void anAnswerWithoutTheDayIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Weather.parse("{\"current\": {\"temperature_2m\": 4}}"));
    }

    @Test
    public void fahrenheitIsAskedForByName() {
        assertTrue(Weather.url(new Routine.Place(41.9028, -87.6837, true)).contains("temperature_unit=fahrenheit"));
    }

    @Test
    public void thePlaceIsSentWithAPoint() {
        assertTrue(Weather.url(new Routine.Place(41.9028, -87.6837, true)).contains("latitude=41.9028&longitude=-87.6837"));
    }
}
