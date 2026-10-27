package ch.deletescape.lawnchair.platform;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

/**
 * Storage access across Android versions.
 *
 * Up to Android 10 the legacy READ/WRITE_EXTERNAL_STORAGE runtime permissions grant access to
 * the shared storage (local backups in Documents/Lawnchair) and to the wallpaper (blur). On newer
 * versions apps targeting a recent SDK need the "All files access" special permission
 * (MANAGE_EXTERNAL_STORAGE) for both.
 */
public final class StorageCompat {

    private StorageCompat() { }

    public static boolean usesAllFilesAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean hasStorageAccess(Context context) {
        if (usesAllFilesAccess()) {
            try {
                return Environment.isExternalStorageManager();
            } catch (RuntimeException e) {
                // No primary storage volume available (e.g. while it is being mounted)
                return false;
            }
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Opens the system screen to grant "All files access" to this app. Only to be used when
     * {@link #usesAllFilesAccess()} returns true.
     */
    public static boolean openAllFilesAccessSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.getPackageName(), null));
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                if (!(context instanceof android.app.Activity)) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(fallback);
                return true;
            } catch (ActivityNotFoundException | SecurityException e2) {
                return false;
            }
        }
    }
}
