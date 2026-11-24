package com.example.message.utils;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import com.example.message.models.Conversation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Copy of the last conversation list shown on the main screen, kept in the app's cache directory.
 *
 * After the app process has ended (swiped away, killed in the background, phone restarted), the main screen had
 * nothing to show until the whole list was rebuilt from the SMS database and contacts. With this copy it shows
 * the previous list at once and replaces it with the fresh one as soon as that is ready.
 *
 * The cache directory is private to the app and is never included in Android backups or device transfers.
 * Fields are written by name without reflection, so code shrinking cannot break the format.
 * Both methods do file I/O: call them off the main thread.
 */
public final class ConversationListCache {

    private static final String TAG = "ConversationListCache";
    private static final String FILE_NAME = "conversation_list_cache.json";
    /** Increase when the stored fields change; files with another version are ignored. */
    private static final int FORMAT_VERSION = 1;

    private ConversationListCache() {}

    /** The saved list, or null if there is none or it cannot be read. */
    public static List<Conversation> read(Context context) {
        File file = new File(context.getCacheDir(), FILE_NAME);
        if (!file.isFile() || file.length() == 0) return null;
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            int version = -1;
            List<Conversation> list = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("version".equals(key)) {
                    version = reader.nextInt();
                } else if ("conversations".equals(key)) {
                    list = readConversations(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return version == FORMAT_VERSION ? list : null;
        } catch (Exception e) {
            Log.w(TAG, "Saved conversation list could not be read, deleting it", e);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
    }

    /** Replaces the saved list. Written to a temporary file first, so a half-written file is never read. */
    public static void write(Context context, List<Conversation> conversations) {
        if (conversations == null) return;
        File dir = context.getCacheDir();
        File tmp = new File(dir, FILE_NAME + ".tmp");
        File file = new File(dir, FILE_NAME);
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            writer.beginObject();
            writer.name("version").value(FORMAT_VERSION);
            writer.name("conversations").beginArray();
            for (Conversation c : conversations) {
                if (c == null) continue;
                writer.beginObject();
                writer.name("threadId").value(c.getThreadId());
                writer.name("address").value(c.getAddress());
                writer.name("name").value(c.getName());
                writer.name("snippet").value(c.getSnippet());
                writer.name("date").value(c.getDate());
                writer.name("read").value(c.isRead());
                writer.name("messageCount").value(c.getMessageCount());
                writer.name("photoUri").value(c.getPhotoUri());
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        } catch (Exception e) {
            Log.w(TAG, "Conversation list could not be saved", e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            Log.w(TAG, "Saved conversation list could not be replaced");
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static List<Conversation> readConversations(JsonReader reader) throws Exception {
        List<Conversation> list = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            long threadId = -1;
            String address = null;
            String name = null;
            String snippet = null;
            long date = 0;
            boolean read = true;
            int messageCount = 0;
            String photoUri = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                switch (key) {
                    case "threadId": threadId = reader.nextLong(); break;
                    case "address": address = reader.nextString(); break;
                    case "name": name = reader.nextString(); break;
                    case "snippet": snippet = reader.nextString(); break;
                    case "date": date = reader.nextLong(); break;
                    case "read": read = reader.nextBoolean(); break;
                    case "messageCount": messageCount = reader.nextInt(); break;
                    case "photoUri": photoUri = reader.nextString(); break;
                    default: reader.skipValue(); break;
                }
            }
            reader.endObject();

            if (threadId > 0) {
                list.add(new Conversation(threadId, address, name, snippet, date, read, messageCount, photoUri));
            }
        }
        reader.endArray();
        return list;
    }
}
