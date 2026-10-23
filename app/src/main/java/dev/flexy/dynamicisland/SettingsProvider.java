package dev.flexy.dynamicisland;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.util.Map;

public final class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!Settings.METHOD_GET.equals(method)) return null;
        SharedPreferences prefs = getContext().getSharedPreferences(Settings.FILE, android.content.Context.MODE_PRIVATE);
        Bundle out = new Bundle();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Integer) out.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Boolean) out.putBoolean(e.getKey(), (Boolean) v);
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
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
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
