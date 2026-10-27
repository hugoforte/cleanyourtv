package io.github.hugoforte.morningboard;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;

/**
 * The install script writes routine.json here with `content write`, since an app's own storage is out of the ADB
 * shell's reach, then asks with `content call --method problem` whether it was taken. Write-only, one file name, and
 * only the ADB shell can call it (WRITE_SECURE_SETTINGS on the provider). The file is taken into the app's storage
 * and deleted once it is closed.
 */
public class ConfigProvider extends ContentProvider {
    static final String PROBLEM = "problem";

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w") || !Store.PUSHED.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        try {
            return ParcelFileDescriptor.open(Store.pushed(getContext()),
                    ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                    new Handler(Looper.getMainLooper()),
                    e -> Store.importPushed(getContext()));
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    /** "problem": takes a pushed file still waiting, then answers why the last one was refused, or "none". */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!PROBLEM.equals(method)) return null;
        // The provider's permission guards files, not call(); the answer can quote the routine, so the same rule.
        if (getContext().checkCallingPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("only the ADB shell may ask");
        }
        Store.importPushed(getContext());
        String problem = Store.problem(getContext());
        Bundle out = new Bundle();
        out.putString(PROBLEM, problem == null ? "none" : problem);
        return out;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
