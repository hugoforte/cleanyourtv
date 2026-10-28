package io.github.hugoforte.morningboard;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Puts the TV back to standby at the end of the window, as Sleep Timer's service does: the "lock screen" global
 * action (Android 9 and later) sends the device to sleep, which an ordinary app cannot do otherwise. The service
 * asks for no events and reads no windows.
 */
public class SleepService extends AccessibilityService {
    private static SleepService instance;

    static boolean sleep() {
        SleepService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
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
