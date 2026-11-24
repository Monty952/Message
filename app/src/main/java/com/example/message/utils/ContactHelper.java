package com.example.message.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.example.message.R;
import com.example.message.models.ContactItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContactHelper {
    // Thread-safe: these caches are filled from several background executors at once (conversation list,
    // chat, search, SMS receiver). A plain HashMap can lose entries or corrupt under concurrent writes.
    // Keys and values are never null here.
    private static final Map<String, String> nameCache = new ConcurrentHashMap<>();
    private static final Map<String, String> photoCache = new ConcurrentHashMap<>();

    public static String getContactName(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return context != null ? context.getString(R.string.unknown) : "Unknown";
        if (phoneNumber.contains(",")) {
            String[] parts = phoneNumber.split(",");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                String singleName = getContactName(context, p.trim());
                if (sb.length() > 0) sb.append(", ");
                sb.append(singleName);
            }
            return sb.toString();
        }
        String cachedName = nameCache.get(phoneNumber);
        if (cachedName != null) {
            return cachedName;
        }

        String name = phoneNumber;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    int photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI);
                    if (nameIdx >= 0) {
                        String lookupName = cursor.getString(nameIdx);
                        if (lookupName != null && !lookupName.isEmpty()) {
                            name = lookupName;
                        }
                    }
                    if (photoIdx >= 0) {
                        String photoUri = cursor.getString(photoIdx);
                        if (photoUri != null) {
                            photoCache.put(phoneNumber, photoUri);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        nameCache.put(phoneNumber, name);
        return name;
    }

    public static String getContactPhotoUri(Context context, String phoneNumber) {
        if (phoneNumber == null) return null;
        if (photoCache.containsKey(phoneNumber)) {
            return photoCache.get(phoneNumber);
        }
        getContactName(context, phoneNumber); // populate cache
        return photoCache.get(phoneNumber);
    }

    public static List<ContactItem> getContacts(Context context) {
        return getAllContacts(context);
    }

    public static List<ContactItem> getAllContacts(Context context) {
        List<ContactItem> list = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        };

        try (Cursor cursor = cr.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI);

                while (cursor.moveToNext()) {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                    String name = nameIdx >= 0 ? cursor.getString(nameIdx) : (context != null ? context.getString(R.string.unknown) : "Unknown");
                    String number = numIdx >= 0 ? cursor.getString(numIdx) : "";
                    String photo = photoIdx >= 0 ? cursor.getString(photoIdx) : null;
                    if (number != null && !number.trim().isEmpty()) {
                        list.add(new ContactItem(id, name, number.replaceAll("\\s+", ""), photo));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
