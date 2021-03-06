package nz.geek.megan.gravasync;

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
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.TextView;

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
import java.util.Map;

public class GravatarSyncHelper {
    private static final String TAG = "SyncTask";

    private Context ctx;

    public GravatarSyncHelper(Context ctx) {
        this.ctx = ctx;
    }

    public void doSync() {
        if (!checkConnectivity()) return;

        // Query Contacts database for all the emails of contacts
        Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Email.DATA
        };

        Cursor c = ctx.getContentResolver().query(uri, projection, null, null, null);

        int idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID);
        int lookupIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.LOOKUP_KEY);
        int dataIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

        // All sorts of wrangling with generics thanks to Java's type erasure.
        ArrayList<Map.Entry<Contact, String>> hashes = new ArrayList<Map.Entry<Contact, String>>();

        if (c.moveToFirst())
        {
            do {
                String email = c.getString(dataIndex);
                String hash = generateGravatarHash(email);
                Log.d(TAG, "Hash: " + hash);

                hashes.add(new AbstractMap.SimpleEntry<Contact, String>(
                        new Contact(c.getLong(idIndex), c.getString(lookupIndex)), hash));


            } while (c.moveToNext());

            Map.Entry<Contact, String>[] a = (Map.Entry<Contact, String>[])new Map.Entry[hashes.size()];

            for (int i = 0; i < hashes.size(); i++) a[i] = hashes.get(i);
//            TextView status_text = (TextView)ctx.findViewById(R.id.status);
//            status_text.setText(String.format(getString(R.string.downloading_status), hashes.size()));
            new DownloadAvatarTask().execute(a);
        }
    }

    private boolean checkConnectivity() {
        ConnectivityManager connMgr = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
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

    private class Contact {
        private long id;
        private String lookupKey;

        public Contact(long id, String lookupKey) {
            this.id = id;
            this.lookupKey = lookupKey;
        }

        public long getId() {
            return id;
        }

        public String getLookupKey() {
            return lookupKey;
        }
    }

    private class DownloadAvatarTask extends AsyncTask<Map.Entry<Contact, String>, Map.Entry<Contact, Bitmap>, Void> {

        private int totalUsers;
        private int processedUsers;

        protected Void doInBackground(Map.Entry<Contact, String>... hashes) {
            totalUsers = hashes.length;
            processedUsers = 0;
            for (int i = 0; i < hashes.length; i++)
            {
                processedUsers++;
                Contact c = hashes[i].getKey();
                // Check whether contact already has a photo
                Uri u = ContactsContract.Contacts.getLookupUri(c.getId(), c.getLookupKey());
                InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), u);
                if (is != null) {
                    try {
                        is.close();
                    }
                    catch (IOException e) {}
                    Log.d(TAG, String.format("Already an avatar for user %d", c.getId()));

                    continue;
                }

                String url = String.format("http://www.gravatar.com/avatar/%s?s=400&d=404", hashes[i].getValue());
                try {
                    Bitmap b = downloadUrl(url);
                    if (b == null) continue;
                    publishProgress(new AbstractMap.SimpleEntry<Contact, Bitmap>(hashes[i].getKey(), b));
                }
                catch (IOException e) {
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Map.Entry<Contact, Bitmap>... values) {
//            TextView status_text = (TextView)findViewById(R.id.status);
            Contact c = values[0].getKey();

            Uri u = ContactsContract.Contacts.getLookupUri(c.getId(), c.getLookupKey());
            String[] projection = { Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.HONEYCOMB ?
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
                    ContactsContract.Contacts.DISPLAY_NAME };

            Cursor cur = ctx.getContentResolver().query(u, projection, null, null, null);

            cur.moveToFirst();

            int i = cur.getColumnIndex(Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.HONEYCOMB ?
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
                    ContactsContract.Contacts.DISPLAY_NAME);

//            status_text.setText(String.format(getString(R.string.progress), cur.getString(i), processedUsers, totalUsers));

            // Compress bitmap from Gravatar and assign it to the contact
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            values[0].getValue().compress(Bitmap.CompressFormat.PNG, 75, stream);
            ArrayList<ContentProviderOperation> operations =
                    new ArrayList<ContentProviderOperation>();
            operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, c.getId())
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
                ctx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
                Log.d(TAG, String.format("Set avatar for %d", c.getId()));
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
//            TextView status_text = (TextView)findViewById(R.id.status);
//            status_text.setText(getString(R.string.finished_status));
        }
    }
}
