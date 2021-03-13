package com.reactnativecommunity.asyncstorage;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.facebook.react.modules.storage.ReactDatabaseSupplier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

// A utility class that migrates a scoped AsyncStorage database to RKStorage.
// This utility only runs if the RKStorage file has not been created yet.
public class AsyncStorageMigration {
    static final String LOG_TAG = "ScopedStorageMigration";

    private static Context mContext;

    public static void migrate(Context context) {
        mContext = context;

        // Only migrate if the default async storage file does not exist.
        if (isAsyncStorageDatabaseCreated()) {
            return;
        }

        ArrayList<File> expoDatabases = getExpoDatabases();

        File expoDatabase = getLastModifiedFile(expoDatabases);

        if (expoDatabase == null) {
            Log.v(LOG_TAG, "No scoped database found");
            return;
        }

        try {
            // Create the storage file
            ReactDatabaseSupplier.getInstance(mContext).get();
            copyFile(new FileInputStream(expoDatabase), new FileOutputStream(mContext.getDatabasePath(ReactDatabaseSupplier.DATABASE_NAME)));
        } catch (Exception e) {
            Log.v(LOG_TAG, "Failed to move scoped database");
            e.printStackTrace();
            return;
        }

        try {
            for (File file : expoDatabases) {
                if (file.delete()) {
                    Log.v(LOG_TAG, "Deleted scoped database " + file.getName());
                } else {
                    Log.v(LOG_TAG, "Failed to delete scoped database " + file.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v(LOG_TAG, "Completed the scoped AsyncStorage migration");
    }

    private static boolean isAsyncStorageDatabaseCreated() {
        return mContext.getDatabasePath(ReactDatabaseSupplier.DATABASE_NAME).exists();
    }

    // Find all database files that the user may have created while using Expo.
    private static ArrayList<File> getExpoDatabases() {
        ArrayList<File> scopedDatabases = new ArrayList<>();
        try {
            File databaseDirectory = mContext.getDatabasePath("noop").getParentFile();
            File[] directoryListing = databaseDirectory.listFiles();
            if (directoryListing != null) {
                for (File child : directoryListing) {
                    // Find all databases matching the Expo scoped key, and skip any database journals.
                    if (child.getName().startsWith("RKStorage-scoped-experience-") && !child.getName().endsWith("-journal")) {
                        scopedDatabases.add(child);
                    }
                }
            }
        } catch (Exception e) {
            // Just in case anything happens catch and print, file system rules can tend to be different across vendors.
            e.printStackTrace();
        }
        return scopedDatabases;
    }

    // Returns the most recently modified file.
    // If a user publishes an app with Expo, then changes the slug 
    // and publishes again, a new database will be created. 
    // We want to select the most recent database and migrate it to RKStorage.
    private static File getLastModifiedFile(ArrayList<File> files) {
        if (files.size() == 0) {
            return null;
        }
        long lastMod = -1;
        File lastModFile = null;
        for (File child : files) {
            long modTime = getLastModifiedTimeInMillis(child);
            if (modTime > lastMod) {
                lastMod = modTime;
                lastModFile = child;
            }
        }
        if (lastModFile != null) {
            return lastModFile;
        }

        return files.get(0);
    }

    private static long getLastModifiedTimeInMillis(File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return getLastModifiedTimeFromBasicFileAttrs(file);
            } else {
                return file.lastModified();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static long getLastModifiedTimeFromBasicFileAttrs(File file) {
        try {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attr.creationTime().toMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void copyFile(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
        FileChannel fromChannel = null;
        FileChannel toChannel = null;
        try {
            fromChannel = fromFile.getChannel();
            toChannel = toFile.getChannel();
            fromChannel.transferTo(0, fromChannel.size(), toChannel);
        } finally {
            try {
                if (fromChannel != null) {
                    fromChannel.close();
                }
            } finally {
                if (toChannel != null) {
                    toChannel.close();
                }
            }
        }
    }
}
