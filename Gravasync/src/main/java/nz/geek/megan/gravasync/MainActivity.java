package nz.geek.megan.gravasync;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.provider.ContactsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String TAG = "SyncTask";

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

    public void doSync(View view) {
        if (!checkConnectivity()) return;

        // Query Contacts database for all the emails of contacts
        Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DATA
        };

        Cursor c = getContentResolver().query(uri, projection, null, null, null);

        int idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID);
        int dataIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

        // All sorts of wrangling with generics thanks to Java's type erasure.
        ArrayList<Map.Entry<String, String>> hashes = new ArrayList<Map.Entry<String, String>>();

        if (c.moveToFirst())
        {
            do {
                String email = c.getString(dataIndex);
                String hash = generateGravatarHash(email);
                Log.d(TAG, "Hash: " + hash);

                hashes.add(new AbstractMap.SimpleEntry<String, String>(c.getString(idIndex), hash));


            } while (c.moveToNext());

            Map.Entry<String, String>[] a = (Map.Entry<String, String>[])new Map.Entry[hashes.size()];

            for (int i = 0; i < hashes.size(); i++) a[i] = hashes.get(i);
            new DownloadAvatarTask().execute(a);
        }
    }

    private boolean checkConnectivity() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    private String generateGravatarHash(String emailAddress) {
        String trimmedLoweredEmail = emailAddress.trim().toLowerCase();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(trimmedLoweredEmail.getBytes("UTF-8"));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }

    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private class DownloadAvatarTask extends AsyncTask<Map.Entry<String, String>, Map.Entry<String, Bitmap>, Void> {

        protected Void doInBackground(Map.Entry<String, String>... hashes) {
            for (int i = 0; i < hashes.length; i++)
            {
                String url = String.format("http://www.gravatar.com/avatar/%s?s=400&d=404", hashes[i].getValue());
                try {
                    Bitmap b = downloadUrl(url);
                    publishProgress(new AbstractMap.SimpleEntry<String, Bitmap>(hashes[i].getKey(), b));
                }
                catch (IOException e) {
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Map.Entry<String, Bitmap>... values) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            values[0].getValue().compress(Bitmap.CompressFormat.PNG, 75, stream);
            ArrayList<ContentProviderOperation> operations =
                    new ArrayList<ContentProviderOperation>();
            operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, values[0].getKey())
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, stream.toByteArray())
                .build());
            try {
                stream.flush();
            } catch (IOException e) {
                Log.d(TAG, "Failed to flush bitmap stream", e);
            }

            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
            } catch (Exception e) {
                Log.d(TAG, "Failed to update avatar", e);
            }
        }

        private Bitmap downloadUrl(String urlString) throws IOException {
            InputStream stream = null;

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();

                if (conn.getResponseCode() == 200)
                {
                    stream = conn.getInputStream();

                    Log.d(TAG, String.format("Downloaded %d bytes", stream.available()));

                    return BitmapFactory.decodeStream(stream);
                } else {
                    return null;
                }
            }
            finally {
                if (stream != null) stream.close();
            }
        }

        @Override
        protected void onPostExecute(Void _) {
//            if (bitmap != null)
//            {
//                Log.d(TAG, String.format("Got an avatar for user %s", bitmap.getKey()));
//
//            }
        }
    }
}


