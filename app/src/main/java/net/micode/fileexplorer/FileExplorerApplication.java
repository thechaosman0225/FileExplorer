package net.micode.fileexplorer;

import be.ppareit.swiftp.App;

import com.google.android.material.color.DynamicColors;

public class FileExplorerApplication extends App {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
