package io.github.hugoforte.youtubechannel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the two youtube.com pages the add-on needs: a channel's /live page, which is the watch page of the stream
 * while one is on, and its /streams tab, the list of its past and upcoming streams. Plain Java, so it is tested on
 * the JVM.
 */
final class ChannelPage {

    /** One stream in the channel's list. */
    static final class Show {
        final String videoId;
        final String title;
        /** What YouTube says about when, such as "Streamed 7d ago"; empty when the page did not say. */
        final String when;

        Show(String videoId, String title, String when) {
            this.videoId = videoId;
            this.title = title;
            this.when = when;
        }
    }

    private static final Pattern CANONICAL_WATCH =
            Pattern.compile("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})\"");
    private static final Pattern CONTENT_ID = Pattern.compile("\"contentId\":\"([A-Za-z0-9_-]{11})\"");
    private static final Pattern TITLE =
            Pattern.compile("\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PART = Pattern.compile("\"text\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    private ChannelPage() {
    }

    /** The video ID of the stream on now, from the channel's /live page, or null when nothing is live. */
    static String liveVideoId(String livePage) {
        Matcher m = CANONICAL_WATCH.matcher(livePage);
        if (!m.find()) return null;
        // An upcoming stream's waiting room is a watch page too; only one on air says isLiveNow.
        return livePage.contains("\"isLiveNow\":true") ? m.group(1) : null;
    }

    /** The streams on the channel's /streams tab, newest first as the page lists them. */
    static List<Show> shows(String streamsPage) {
        List<Show> shows = new ArrayList<>();
        String[] lockups = streamsPage.split("\"lockupViewModel\":\\{");
        for (int i = 1; i < lockups.length; i++) {
            String lockup = lockups[i];
            Matcher id = CONTENT_ID.matcher(lockup);
            Matcher title = TITLE.matcher(lockup);
            if (!id.find() || !title.find()) continue;
            String when = "";
            Matcher part = PART.matcher(lockup.substring(title.end()));
            while (part.find()) {
                String text = unescape(part.group(1));
                if (text.startsWith("Streamed") || text.startsWith("Scheduled") || text.contains("watching")) {
                    when = text;
                    break;
                }
            }
            shows.add(new Show(id.group(1), unescape(title.group(1)), when));
        }
        return shows;
    }

    /** Undoes the JSON string escapes YouTube writes into its pages. */
    static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 == s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    break;
                default: out.append(n);
            }
        }
        return out.toString();
    }
}
