package com.pixel12.qs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Lets the SystemUI hook read the app's settings across processes. */
public class SettingsProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        Context c = getContext();
        if (c == null) return b;
        SharedPreferences sp = c.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
        for (String k : Config.KEYS) {
            b.putInt(k, sp.getInt(k, Config.def(k)));
        }
        return b;
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
