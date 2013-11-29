package nz.geek.megan.gravasync;

import android.app.IntentService;
import android.content.Intent;

/**
 * A background service for syncing contact pictures with Gravasync.
 */
public class GravasyncService extends IntentService {
    @Override
    protected void onHandleIntent(Intent workIntent) {
        GravatarSyncHelper helper = new GravatarSyncHelper(getApplicationContext());
        helper.doSync();
    }

    public GravasyncService() {
        super("GravasyncService");
    }
}
