package io.github.hugoforte.morningboard;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * The board's two reaches into the rest of the TV, neither of which an ordinary app has. sleep() puts the TV back
 * to standby at the end of the window with the "lock screen" global action (Android 9 and later), as Sleep Timer's
 * service does. press() clicks the focused control in the music app's window: a playlist opened by link shows its
 * Play button focused, and Spotify's TV app plays a playlist only that way. It reads no other app's windows
 * (packageNames in res/xml/sleep_service.xml) and asks for no events.
 */
public class SleepService extends AccessibilityService {
    private static final String TAG = "MorningBoard";
    private static SleepService instance;

    static boolean sleep() {
        SleepService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /**
     * Clicks pkg's Play button: the focused control of its window, else a clickable control named Play. Says in the
     * log which step found nothing, since the TV shows none of it. False when there was nothing to click.
     */
    static boolean press(String pkg) {
        SleepService service = instance;
        if (service == null) {
            Log.w(TAG, "press: the accessibility service is not connected");
            return false;
        }
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo w : service.getWindows()) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && r.getPackageName() != null && pkg.contentEquals(r.getPackageName())) root = r;
        }
        if (root == null) {
            Log.w(TAG, "press: no window of " + pkg + " among " + service.getWindows().size());
            return false;
        }
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null) target = named(root, "play");
        if (target == null) {
            Log.w(TAG, "press: nothing focused and no Play control in " + pkg);
            return false;
        }
        // Spotify's TV app marks its focused Play button neither clickable nor inside a clickable parent, but may
        // still take the click; so the click is offered to the node and then to each node above it.
        for (AccessibilityNodeInfo n = target; n != null; n = n.getParent()) {
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "press: clicked " + n.getClassName() + " " + n.getContentDescription());
                return true;
            }
        }
        Log.w(TAG, "press: nothing took the click, from " + target.getClassName() + " " + target.getContentDescription()
                + " " + target.getText());
        return false;
    }

    /** The first node under n whose content description or text is the word, ignoring case. */
    private static AccessibilityNodeInfo named(AccessibilityNodeInfo n, String word) {
        if (n == null) return null;
        CharSequence label = n.getContentDescription() != null ? n.getContentDescription() : n.getText();
        if (label != null && label.toString().trim().equalsIgnoreCase(word)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo found = named(n.getChild(i), word);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
