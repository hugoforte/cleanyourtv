package io.github.hugoforte.youtubechannel;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The card: opens the channel's stream in YouTube when one is on, and otherwise lists its past streams to pick from.
 * When the list cannot be read, it opens the channel in YouTube instead.
 */
public class ChannelActivity extends Activity {

    private static final String TAG = "YouTubeChannel";
    private static final String YOUTUBE_TV = "com.google.android.youtube.tv";

    private final ExecutorService work = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 20, 20));
        root.setPadding(dp(48), dp(32), dp(48), dp(16));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        status.setText(getString(R.string.checking, getString(R.string.app_name)));
        root.addView(status);
        list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(8));
        list.setSelector(new ColorDrawable(Color.rgb(70, 70, 70)));
        list.setPadding(0, dp(16), 0, 0);
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        check();
    }

    @Override
    protected void onDestroy() {
        work.shutdownNow();
        super.onDestroy();
    }

    private void check() {
        String channel = "https://www.youtube.com/channel/" + getString(R.string.channel_id);
        work.execute(() -> {
            try {
                String live = ChannelPage.liveVideoId(fetch(channel + "/live"));
                if (live != null) {
                    main.post(() -> {
                        open("https://www.youtube.com/watch?v=" + live);
                        finish();
                    });
                    return;
                }
                List<ChannelPage.Show> shows = ChannelPage.shows(fetch(channel + "/streams"));
                main.post(() -> {
                    if (shows.isEmpty()) {
                        openChannel(channel);
                    } else {
                        pick(shows);
                    }
                });
            } catch (IOException e) {
                Log.w(TAG, "could not read the channel", e);
                main.post(() -> openChannel(channel));
            }
        });
    }

    private void openChannel(String channel) {
        open(channel + "/streams");
        finish();
    }

    private void pick(List<ChannelPage.Show> shows) {
        status.setText(getString(R.string.not_live, getString(R.string.app_name)));
        list.setAdapter(new ShowAdapter(shows));
        list.setOnItemClickListener((parent, view, position, id) ->
                open("https://www.youtube.com/watch?v=" + shows.get(position).videoId));
        list.requestFocus();
    }

    private void open(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(YOUTUBE_TV);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    private static String fetch(String url) throws IOException {
        return new String(read(url), StandardCharsets.UTF_8);
    }

    private static byte[] read(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        // The desktop page, in English, past the cookie consent page: what ChannelPage reads.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36");
        c.setRequestProperty("Accept-Language", "en-US,en");
        c.setRequestProperty("Cookie", "SOCS=CAI");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class ShowAdapter extends BaseAdapter {
        private final List<ChannelPage.Show> shows;

        ShowAdapter(List<ChannelPage.Show> shows) {
            this.shows = shows;
        }

        @Override public int getCount() { return shows.size(); }
        @Override public Object getItem(int position) { return shows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = new LinearLayout(ChannelActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(8), dp(8), dp(8));
                ImageView thumb = new ImageView(ChannelActivity.this);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(thumb, new LinearLayout.LayoutParams(dp(192), dp(108)));
                LinearLayout text = new LinearLayout(ChannelActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setPadding(dp(24), 0, 0, 0);
                TextView title = new TextView(ChannelActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                title.setMaxLines(2);
                TextView when = new TextView(ChannelActivity.this);
                when.setTextColor(Color.LTGRAY);
                when.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                text.addView(title);
                text.addView(when);
                row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            ChannelPage.Show show = shows.get(position);
            ImageView thumb = (ImageView) row.getChildAt(0);
            LinearLayout text = (LinearLayout) row.getChildAt(1);
            ((TextView) text.getChildAt(0)).setText(show.title);
            ((TextView) text.getChildAt(1)).setText(show.when);
            thumb.setImageDrawable(null);
            thumb.setTag(show.videoId);
            work.execute(() -> {
                try {
                    byte[] jpg = read("https://i.ytimg.com/vi/" + show.videoId + "/mqdefault.jpg");
                    Bitmap bitmap = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                    main.post(() -> {
                        if (show.videoId.equals(thumb.getTag())) thumb.setImageBitmap(bitmap);
                    });
                } catch (IOException e) {
                    Log.w(TAG, "no thumbnail for " + show.videoId, e);
                }
            });
            return row;
        }
    }
}
