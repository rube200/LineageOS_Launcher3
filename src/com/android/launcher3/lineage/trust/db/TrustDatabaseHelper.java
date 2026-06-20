/*
 * Copyright (C) 2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.lineage.trust.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TrustDatabaseHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "trust_apps_db";

    private static final String TABLE_NAME = "trust_apps";
    private static final String KEY_UID = "uid";
    private static final String KEY_PKGNAME = "pkgname";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_PROTECTED = "protected";

    @Nullable
    private static TrustDatabaseHelper sSingleton;

    private TrustDatabaseHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized TrustDatabaseHelper getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            sSingleton = new TrustDatabaseHelper(context.getApplicationContext());
        }

        return sSingleton;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CMD_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
                "(" +
                KEY_UID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                KEY_PKGNAME + " TEXT NOT NULL UNIQUE," +
                KEY_HIDDEN + " INTEGER DEFAULT 0," +
                KEY_PROTECTED + " INTEGER DEFAULT 0" +
                ")";
        db.execSQL(CMD_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE trust_apps_new ("
                    + KEY_UID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + KEY_PKGNAME + " TEXT NOT NULL UNIQUE,"
                    + KEY_HIDDEN + " INTEGER DEFAULT 0,"
                    + KEY_PROTECTED + " INTEGER DEFAULT 0"
                    + ")");
            db.execSQL("INSERT INTO trust_apps_new (" + KEY_PKGNAME + ", " + KEY_HIDDEN + ", "
                    + KEY_PROTECTED + ") SELECT " + KEY_PKGNAME + ", MAX(" + KEY_HIDDEN + "), "
                    + "MAX(" + KEY_PROTECTED + ") FROM " + TABLE_NAME + " GROUP BY "
                    + KEY_PKGNAME);
            db.execSQL("DROP TABLE " + TABLE_NAME);
            db.execSQL("ALTER TABLE trust_apps_new RENAME TO " + TABLE_NAME);
        }
    }

    public boolean addHiddenApp(@NonNull String packageName) {
        if (isPackageHidden(packageName)) {
            return true;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_PKGNAME, packageName);
            values.put(KEY_HIDDEN, 1);

            int rows = db.update(TABLE_NAME, values, KEY_PKGNAME + " = ?",
                    new String[]{packageName});
            if (rows != 1) {
                db.insertOrThrow(TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
            return isPackageHidden(packageName);
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean addProtectedApp(@NonNull String packageName) {
        if (isPackageProtected(packageName)) {
            return true;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_PKGNAME, packageName);
            values.put(KEY_PROTECTED, 1);

            int rows = db.update(TABLE_NAME, values, KEY_PKGNAME + " = ?",
                    new String[]{packageName});
            if (rows != 1) {
                db.insertOrThrow(TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
            return isPackageProtected(packageName);
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean removeHiddenApp(@NonNull String packageName) {
        if (!isPackageHidden(packageName)) {
            return true;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_HIDDEN, 0);

            db.update(TABLE_NAME, values, KEY_PKGNAME + " = ?", new String[]{packageName});
            db.setTransactionSuccessful();
            return !isPackageHidden(packageName);
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean removeProtectedApp(@NonNull String packageName) {
        if (!isPackageProtected(packageName)) {
            return true;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_PROTECTED, 0);

            db.update(TABLE_NAME, values, KEY_PKGNAME + " = ?", new String[]{packageName});
            db.setTransactionSuccessful();
            return !isPackageProtected(packageName);
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        String query = String.format("SELECT 1 FROM %s WHERE %s = ? AND %s = ? LIMIT 1",
                TABLE_NAME, KEY_PKGNAME, KEY_HIDDEN);
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(query,
                new String[]{packageName, String.valueOf(1)})) {
            return cursor.getCount() != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPackageProtected(@NonNull String packageName) {
        String query = String.format("SELECT 1 FROM %s WHERE %s = ? AND %s = ? LIMIT 1",
                TABLE_NAME, KEY_PKGNAME, KEY_PROTECTED);
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(query,
                new String[]{packageName, String.valueOf(1)})) {
            return cursor.getCount() != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasAnyProtectedApp() {
        String query = String.format("SELECT 1 FROM %s WHERE %s = 1 LIMIT 1", TABLE_NAME,
                KEY_PROTECTED);
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(query)) {
            return cursor.getCount() != 0;
        }
    }
}
