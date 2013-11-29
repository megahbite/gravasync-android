package nz.geek.megan.gravasync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void doSync(View _) {
        GravatarSyncHelper helper = new GravatarSyncHelper(getApplicationContext());
        helper.doSync();

//        Intent syncIntent = new Intent(getApplicationContext(), GravasyncService.class);
//        PendingIntent pendingSyncIntent = PendingIntent.getService(getApplicationContext(), 2222,
//                syncIntent, PendingIntent.FLAG_CANCEL_CURRENT);

//        Calendar c = Calendar.getInstance();
//        cal.add(Calendar.)

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}


