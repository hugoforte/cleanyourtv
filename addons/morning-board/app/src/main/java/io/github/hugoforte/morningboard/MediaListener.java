package io.github.hugoforte.morningboard;

import android.service.notification.NotificationListenerService;

/**
 * Reads no notifications. Android hands an app the other apps' media sessions only while it holds an allowed
 * notification listener, and Music needs the music app's session to tell its player what to play.
 */
public class MediaListener extends NotificationListenerService {
}
