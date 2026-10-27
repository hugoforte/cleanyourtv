package io.github.hugoforte.morningboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The install script writes routine.json here with `content write`, since an app's own storage is out of the ADB
 * shell's reach. Write-only, one file name, and only the ADB shell can call it (WRITE_SECURE_SETTINGS on the
 * provider). The file is taken into the app's storage and deleted once it is closed.
 */
public class ConfigProvider extends ContentProvider {
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w") || !Store.PUSHED.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        File dir = getContext().getExternalFilesDir(null);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) throw new FileNotFoundException("no files folder");
        try {
            return ParcelFileDescriptor.open(new File(dir, Store.PUSHED),
                    ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                    new Handler(Looper.getMainLooper()),
                    e -> Store.importPushed(getContext()));
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
