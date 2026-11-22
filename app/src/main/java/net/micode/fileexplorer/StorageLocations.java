package net.micode.fileexplorer;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StorageLocations {
    public static final class Location {
        public final String path;
        public final String name;
        public final boolean primary;

        private Location(String path, String name, boolean primary) {
            this.path = path;
            this.name = name;
            this.primary = primary;
        }
    }

    private StorageLocations() {
    }

    public static List<Location> mounted(Context context) {
        Map<String, Location> locations = new LinkedHashMap<>();
        File primary = Environment.getExternalStorageDirectory();
        addIfMounted(locations, primary, context.getString(R.string.storage_internal), true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageManager storageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (storageManager != null) {
                for (StorageVolume volume : storageManager.getStorageVolumes()) {
                    File root = volume.getDirectory();
                    if (root == null || root.equals(primary)) {
                        continue;
                    }
                    addIfMounted(locations, root, volume.getDescription(context), false);
                }
            }
        }

        for (File appDirectory : ContextCompat.getExternalFilesDirs(context, null)) {
            File root = rootFromAppDirectory(appDirectory);
            if (root != null && !root.equals(primary)) {
                addIfMounted(locations, root, root.getName(), false);
            }
        }
        return new ArrayList<>(locations.values());
    }

    public static Location containing(List<Location> locations, String path) {
        if (path == null) {
            return null;
        }
        Location match = null;
        for (Location location : locations) {
            if ((path.equals(location.path) || path.startsWith(location.path + File.separator))
                    && (match == null || location.path.length() > match.path.length())) {
                match = location;
            }
        }
        return match;
    }

    private static File rootFromAppDirectory(File appDirectory) {
        if (appDirectory == null) {
            return null;
        }
        File root = appDirectory;
        for (int i = 0; i < 4 && root != null; i++) {
            root = root.getParentFile();
        }
        return root;
    }

    private static void addIfMounted(Map<String, Location> locations, File root, String name,
            boolean primary) {
        if (root == null) {
            return;
        }
        String state = Environment.getExternalStorageState(root);
        if (!Environment.MEDIA_MOUNTED.equals(state)
                && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return;
        }
        String path = root.getAbsolutePath();
        String label = name == null || name.isEmpty() ? root.getName() : name;
        if (!locations.containsKey(path)) {
            locations.put(path, new Location(path, label, primary));
        }
    }
}
