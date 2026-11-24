package com.example.message.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Patterns;

import com.example.message.R;
import com.example.message.database.AppDatabase;
import com.example.message.models.Conversation;
import com.example.message.models.MessageItem;
import com.example.message.receivers.SmsStatusReceiver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SmsHelper {

    private static final Uri CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations?simple=true");
    private static final Uri CANONICAL_ADDRESSES_URI = Uri.parse("content://mms-sms/canonical-addresses");
    private static final Uri MMS_URI = Uri.parse("content://mms");
    private static final Uri MMS_PART_URI = Uri.parse("content://mms/part");

    // Real MMS messages only, same rule the provider uses for message_count: not drafts (msg_box 3), and only
    // m_type 128 (sent), 130 (notification, not downloaded yet) and 132 (received). This excludes delivery and read reports.
    private static final String MMS_MESSAGE_SELECTION =
            "thread_id = ? AND msg_box != 3 AND (m_type = 128 OR m_type = 130 OR m_type = 132)";
    private static final int MMS_PART_BATCH_SIZE = 200;

    /** Latest displayable message of a thread, used when the provider's snippet is blank. */
    private static final class MessagePreview {
        final String text;
        final long date;

        MessagePreview(String text, long date) {
            this.text = text;
            this.date = date;
        }
    }

    /**
     * A text message is valid only if its body has at least one non-whitespace character.
     * Used to stop empty text messages from being stored or displayed.
     */
    public static boolean isValidMessageBody(String body) {
        return body != null && !body.trim().isEmpty();
    }

    public static List<Conversation> getConversations(Context context) {
        return getConversations(context, false, false);
    }

    public static List<MessageItem> getMessages(Context context, long threadId) {
        return getMessagesForThread(context, threadId);
    }

    public static void markThreadRead(Context context, long threadId) {
        markThreadAsRead(context, threadId);
    }

    public static boolean deleteThread(Context context, long threadId) {
        return deleteSystemThread(context, threadId);
    }

    public static boolean deleteMessage(Context context, long messageId) {
        return deleteSystemMessage(context, messageId);
    }

    public static List<Conversation> searchConversations(Context context, String query) {
        List<Conversation> all = getConversations(context);
        if (query == null || query.trim().isEmpty()) return all;
        String q = query.toLowerCase().trim();
        List<Conversation> matches = new ArrayList<>();
        for (Conversation c : all) {
            boolean nameMatch = c.getContactName() != null && c.getContactName().toLowerCase().contains(q);
            boolean addrMatch = c.getAddress() != null && c.getAddress().contains(q);
            boolean snippetMatch = c.getSnippet() != null && c.getSnippet().toLowerCase().contains(q);
            if (nameMatch || addrMatch || snippetMatch) {
                matches.add(c);
            }
        }
        return matches;
    }

    public static boolean insertMessage(Context context, MessageItem item) {
        // Never write a text message without content (e.g. empty drafts captured in older backups).
        if (item == null || !isValidMessageBody(item.getBody())) {
            return false;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("address", item.getAddress());
            values.put("body", item.getBody());
            values.put("date", item.getDate());
            values.put("read", item.isRead() ? 1 : 0);
            values.put("type", item.getType());
            values.put("thread_id", item.getThreadId() > 0 ? item.getThreadId() : getOrCreateThreadId(context, item.getAddress()));

            Uri targetUri = item.isIncoming() ? Telephony.Sms.Inbox.CONTENT_URI : Telephony.Sms.Sent.CONTENT_URI;
            context.getContentResolver().insert(targetUri, values);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isDefaultSmsApp(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager roleManager = context.getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        try {
            String defaultPkg = Telephony.Sms.getDefaultSmsPackage(context);
            return defaultPkg != null && defaultPkg.equals(context.getPackageName());
        } catch (Exception ignored) {}
        return false;
    }

    public static void requestDefaultSmsRole(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = activity.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                activity.startActivityForResult(intent, requestCode);
                return;
            }
        }
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.getPackageName());
        activity.startActivityForResult(intent, requestCode);
    }

    public static List<Conversation> getConversations(Context context, boolean includeArchived, boolean includeRecycleBin) {
        List<Conversation> list = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        // Load filtered thread IDs
        AppDatabase db = AppDatabase.getInstance(context);
        Set<Long> archivedSet = new HashSet<>(db.archiveDao().getArchivedThreadIdsSync());
        Set<Long> recycleBinSet = new HashSet<>(db.recycleBinDao().getRecycleBinThreadIdsSync());

        Uri uri = CONVERSATIONS_URI;
        String[] projection = new String[]{"_id", "date", "message_count", "recipient_ids", "snippet", "read"};

        // Number of thread rows the provider returned, before any filtering. Used to decide on the fallback.
        int providerRowCount = 0;
        // Set when a conversation's date was replaced by the date of its latest real message.
        boolean needsResort = false;
        // Threads to show, read from the cursor first. Their per-conversation lookups run afterwards (see below).
        List<ThreadRow> rows = new ArrayList<>();
        Map<String, String> canonicalAddresses = Collections.emptyMap();

        try (Cursor cursor = cr.query(uri, projection, null, null, "date DESC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int dateIdx = cursor.getColumnIndex("date");
                int countIdx = cursor.getColumnIndex("message_count");
                int recipIdx = cursor.getColumnIndex("recipient_ids");
                int snippetIdx = cursor.getColumnIndex("snippet");
                int readIdx = cursor.getColumnIndex("read");

                providerRowCount = cursor.getCount();

                // Resolve all recipient addresses with ONE query instead of one query per conversation.
                canonicalAddresses = providerRowCount > 0
                        ? loadCanonicalAddresses(cr)
                        : Collections.<String, String>emptyMap();

                while (cursor.moveToNext()) {
                    long threadId = cursor.getLong(idIdx);

                    // Filter based on requested scope
                    boolean isArchived = archivedSet.contains(threadId);
                    boolean isDeleted = recycleBinSet.contains(threadId);

                    if (!includeArchived && isArchived) continue;
                    if (includeArchived && !isArchived) continue;

                    if (!includeRecycleBin && isDeleted) continue;
                    if (includeRecycleBin && !isDeleted) continue;

                    long date = cursor.getLong(dateIdx);
                    int count = countIdx >= 0 ? cursor.getInt(countIdx) : 0;
                    String recipIds = cursor.getString(recipIdx);
                    String snippet = snippetIdx >= 0 ? cursor.getString(snippetIdx) : "";
                    boolean read = readIdx >= 0 && cursor.getInt(readIdx) == 1;

                    // Skip empty threads: the threads table keeps rows with no messages, for example rows
                    // created by Telephony.Threads.getOrCreateThreadId() when a chat is opened but nothing is
                    // sent. A thread is skipped only when it has no messages AND no snippet.
                    if (countIdx >= 0 && count <= 0 && !isValidMessageBody(snippet)) continue;

                    rows.add(new ThreadRow(threadId, date, count, recipIds, snippet, read));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Each conversation needs its own provider queries (contact name and photo, and a preview when the snippet
        // is blank). They used to run one after another, and the list was shown only after all of them finished,
        // which is most of the time the app needed to show its conversations after starting. They now run in
        // parallel. Results and order are the same as before.
        Conversation[] resolved = resolveConversations(context, rows, canonicalAddresses);
        for (int i = 0; i < resolved.length; i++) {
            Conversation conversation = resolved[i];
            if (conversation == null) continue; // thread without anything to show
            if (conversation.getDate() != rows.get(i).date) {
                needsResort = true;
            }
            list.add(conversation);
        }

        // Fallback: If query fails or returns empty due to manufacturer difference, query Telephony.Sms directly.
        // It runs only when the provider itself returned no thread rows. Previously it also ran when rows existed
        // but were all filtered out (archived, recycle bin or empty), which scanned the whole SMS table for nothing.
        if (providerRowCount == 0 && list.isEmpty() && !includeArchived && !includeRecycleBin) {
            list.addAll(getConversationsFallback(context, archivedSet, recycleBinSet));
        }

        if (needsResort) {
            // Keep newest-first order after a date was corrected above (stable sort, others keep their order).
            Collections.sort(list, (a, b) -> Long.compare(b.getDate(), a.getDate()));
        }

        return list;
    }

    /** One row of the threads table, before its per-conversation lookups. */
    private static final class ThreadRow {
        final long threadId;
        final long date;
        final int count;
        final String recipientIds;
        final String snippet;
        final boolean read;

        ThreadRow(long threadId, long date, int count, String recipientIds, String snippet, boolean read) {
            this.threadId = threadId;
            this.date = date;
            this.count = count;
            this.recipientIds = recipientIds;
            this.snippet = snippet;
            this.read = read;
        }
    }

    /** Parallel lookups only pay off when there are several conversations. */
    private static final int PARALLEL_LOOKUP_MIN_ROWS = 8;
    private static final int LOOKUP_THREADS = 4;
    private static volatile ExecutorService lookupExecutor;

    /** Shared pool for conversation lookups. Its threads stop after 30 s without work. */
    private static ExecutorService getLookupExecutor() {
        if (lookupExecutor == null) {
            synchronized (SmsHelper.class) {
                if (lookupExecutor == null) {
                    ThreadPoolExecutor executor = new ThreadPoolExecutor(LOOKUP_THREADS, LOOKUP_THREADS,
                            30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
                                Thread thread = new Thread(runnable, "conversation-lookup");
                                thread.setDaemon(true);
                                return thread;
                            });
                    executor.allowCoreThreadTimeOut(true);
                    lookupExecutor = executor;
                }
            }
        }
        return lookupExecutor;
    }

    /**
     * Builds the conversations for the given rows, in the same order. An entry is null when the thread has nothing
     * to show. Runs the rows on the lookup pool; the provider queries are independent of each other, and the
     * contact caches they fill are thread-safe.
     */
    private static Conversation[] resolveConversations(Context context, List<ThreadRow> rows, Map<String, String> canonicalAddresses) {
        Conversation[] result = new Conversation[rows.size()];
        if (rows.size() < PARALLEL_LOOKUP_MIN_ROWS) {
            for (int i = 0; i < rows.size(); i++) {
                result[i] = resolveConversationSafely(context, rows.get(i), canonicalAddresses);
            }
            return result;
        }

        List<Callable<Conversation>> tasks = new ArrayList<>(rows.size());
        for (ThreadRow row : rows) {
            tasks.add(() -> resolveConversationSafely(context, row, canonicalAddresses));
        }
        try {
            List<Future<Conversation>> futures = getLookupExecutor().invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    result[i] = futures.get(i).get();
                } catch (ExecutionException e) {
                    Log.w("SMS_DEBUG", "resolveConversations: Lookup failed for thread " + rows.get(i).threadId, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w("SMS_DEBUG", "resolveConversations: Interrupted, returning the conversations resolved so far");
        } catch (Exception e) {
            // The pool could not run the lookups: do them here, one after another, as before.
            Log.w("SMS_DEBUG", "resolveConversations: Parallel lookup unavailable, resolving sequentially", e);
            for (int i = 0; i < rows.size(); i++) {
                result[i] = resolveConversationSafely(context, rows.get(i), canonicalAddresses);
            }
        }
        return result;
    }

    private static Conversation resolveConversationSafely(Context context, ThreadRow row, Map<String, String> canonicalAddresses) {
        try {
            return resolveConversation(context, row, canonicalAddresses);
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "resolveConversation: Failed for thread " + row.threadId, e);
            return null;
        }
    }

    /** The per-conversation part of getConversations(), unchanged in behaviour. Returns null to skip the thread. */
    private static Conversation resolveConversation(Context context, ThreadRow row, Map<String, String> canonicalAddresses) {
        String snippet = row.snippet;
        long date = row.date;

        // The provider's snippet is blank when the newest message is an MMS (its snippet is the
        // subject, usually empty) or an SMS row with an empty body. Use the latest real message preview.
        if (!isValidMessageBody(snippet)) {
            MessagePreview preview = getLatestMessagePreview(context, row.threadId);
            if (preview != null && isValidMessageBody(preview.text)) {
                snippet = preview.text;
                if (preview.date > 0 && preview.date != date) {
                    date = preview.date;
                }
            } else if (row.count > 0) {
                // The thread holds real messages (e.g. media-only MMS). Use a meaningful label instead of dropping it.
                snippet = context.getString(R.string.mms_message);
            } else {
                return null;
            }
        }

        String address = resolveRecipientAddress(context, row.recipientIds, canonicalAddresses);
        String name = ContactHelper.getContactName(context, address);
        String photoUri = ContactHelper.getContactPhotoUri(context, address);

        return new Conversation(row.threadId, address, name, snippet, date, row.read, row.count, photoUri);
    }

    /**
     * Returns the latest message of a thread that has something to show: the newest SMS with text, or the newest
     * MMS (its text, or a "Multimedia message" label for media-only MMS). Returns null if the thread only holds
     * empty SMS rows.
     */
    private static MessagePreview getLatestMessagePreview(Context context, long threadId) {
        MessagePreview smsPreview = null;
        try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{"body", "date"}, "thread_id = ?",
                new String[]{String.valueOf(threadId)}, "date DESC")) {
            if (cursor != null) {
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");
                while (cursor.moveToNext()) {
                    String body = cursor.getString(bodyIdx);
                    if (isValidMessageBody(body)) {
                        smsPreview = new MessagePreview(body, cursor.getLong(dateIdx));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        MessagePreview mmsPreview = null;
        List<MessageItem> latestMms = loadMmsMessages(context, threadId, 1);
        if (!latestMms.isEmpty()) {
            MessageItem mms = latestMms.get(0);
            mmsPreview = new MessagePreview(mms.getBody(), mms.getDate());
        }

        if (smsPreview == null) return mmsPreview;
        if (mmsPreview == null) return smsPreview;
        return mmsPreview.date > smsPreview.date ? mmsPreview : smsPreview;
    }

    /**
     * All displayable messages of a thread, SMS and MMS, oldest first. The chat screen used to read only the SMS
     * table, so conversations made of MMS opened empty.
     */
    public static List<MessageItem> getConversationMessages(Context context, long threadId) {
        List<MessageItem> list = getMessagesForThread(context, threadId);
        List<MessageItem> mmsList = loadMmsMessages(context, threadId, 0);
        if (!mmsList.isEmpty()) {
            list.addAll(mmsList);
            Collections.sort(list, (a, b) -> Long.compare(a.getDate(), b.getDate()));
        }
        return list;
    }

    /**
     * Reads MMS messages of a thread, newest first. Each MMS becomes a MessageItem whose body is its text parts,
     * or its subject, or a "Multimedia message" label when it only has media (or is not downloaded yet), so a
     * real MMS never appears as an empty message.
     *
     * @param limit maximum number of messages to read, or 0 for all
     */
    private static List<MessageItem> loadMmsMessages(Context context, long threadId, int limit) {
        List<MessageItem> result = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        List<Long> ids = new ArrayList<>();
        Map<Long, long[]> meta = new HashMap<>();      // id -> {dateMs, msgBox, read}
        Map<Long, String> subjects = new HashMap<>();
        Map<Long, Integer> mTypes = new HashMap<>();
        try (Cursor cursor = cr.query(MMS_URI, new String[]{"_id", "date", "msg_box", "read", "sub", "m_type"},
                MMS_MESSAGE_SELECTION, new String[]{String.valueOf(threadId)}, "date DESC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int dateIdx = cursor.getColumnIndex("date");
                int boxIdx = cursor.getColumnIndex("msg_box");
                int readIdx = cursor.getColumnIndex("read");
                int subIdx = cursor.getColumnIndex("sub");
                int typeIdx = cursor.getColumnIndex("m_type");
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIdx);
                    long dateMs = cursor.getLong(dateIdx) * 1000L; // MMS dates are stored in seconds
                    long box = boxIdx >= 0 ? cursor.getLong(boxIdx) : Telephony.Mms.MESSAGE_BOX_INBOX;
                    long read = readIdx >= 0 ? cursor.getLong(readIdx) : 1;
                    int mType = typeIdx >= 0 ? cursor.getInt(typeIdx) : 132;
                    ids.add(id);
                    meta.put(id, new long[]{dateMs, box, read});
                    mTypes.put(id, mType);
                    if (subIdx >= 0) {
                        subjects.put(id, cursor.getString(subIdx));
                    }
                    if (limit > 0 && ids.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ids.isEmpty()) return result;

        Map<Long, StringBuilder> texts = new HashMap<>();
        Map<Long, String> mediaUris = new HashMap<>();
        Map<Long, String> mediaTypes = new HashMap<>();
        Map<Long, String> addresses = new HashMap<>();

        // First check if our app has saved local media files for any of these MMS messages
        for (Long id : ids) {
            File localMedia = MmsHelper.findSavedMediaFile(context, id);
            if (localMedia != null && localMedia.exists() && localMedia.length() > 0) {
                mediaUris.put(id, Uri.fromFile(localMedia).toString());
                mediaTypes.put(id, MmsHelper.getWellKnownContentType(0x3E)); // image default
                Log.d("MMS_DEBUG", "SmsHelper.loadMmsMessages: Found saved local media file for MMS " + id
                        + ": " + localMedia.getAbsolutePath() + " (" + localMedia.length() + " bytes)");
            }
        }

        for (int start = 0; start < ids.size(); start += MMS_PART_BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(ids.size(), start + MMS_PART_BATCH_SIZE));
            StringBuilder in = new StringBuilder();
            for (Long id : batch) {
                if (in.length() > 0) in.append(',');
                in.append(id);
            }
            try (Cursor cursor = cr.query(MMS_PART_URI, new String[]{"_id", "mid", "ct", "text"},
                    "mid IN (" + in + ")", null, null)) {
                if (cursor != null) {
                    int partIdIdx = cursor.getColumnIndex("_id");
                    int midIdx = cursor.getColumnIndex("mid");
                    int ctIdx = cursor.getColumnIndex("ct");
                    int textIdx = cursor.getColumnIndex("text");
                    while (cursor.moveToNext()) {
                        long mid = cursor.getLong(midIdx);
                        String ct = cursor.getString(ctIdx);
                        if ("application/smil".equalsIgnoreCase(ct)) continue; // layout description, not content
                        if ("text/plain".equalsIgnoreCase(ct)) {
                            String text = textIdx >= 0 ? cursor.getString(textIdx) : null;
                            if (text == null) {
                                // Large text parts are stored in a file instead of the text column
                                text = readMmsTextPart(cr, cursor.getLong(partIdIdx));
                            }
                            if (isValidMessageBody(text)) {
                                StringBuilder sb = texts.get(mid);
                                if (sb == null) {
                                    sb = new StringBuilder();
                                    texts.put(mid, sb);
                                } else {
                                    sb.append('\n');
                                }
                                sb.append(text.trim());
                            }
                        } else if (ct != null) {
                            if (!mediaUris.containsKey(mid)) {
                                long partId = cursor.getLong(partIdIdx);
                                mediaUris.put(mid, ContentUris.withAppendedId(MMS_PART_URI, partId).toString());
                                mediaTypes.put(mid, ct);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Per-message fallback for any MMS that didn't get resolved by batch query
        for (Long id : ids) {
            if (!mediaUris.containsKey(id) && (!texts.containsKey(id) || texts.get(id).length() == 0)) {
                try (Cursor c = cr.query(Uri.parse("content://mms/" + id + "/part"),
                        new String[]{"_id", "ct", "text"}, null, null, null)) {
                    if (c != null) {
                        int partIdIdx = c.getColumnIndex("_id");
                        int ctIdx = c.getColumnIndex("ct");
                        int textIdx = c.getColumnIndex("text");
                        while (c.moveToNext()) {
                            String ct = c.getString(ctIdx);
                            if ("application/smil".equalsIgnoreCase(ct)) continue;
                            if ("text/plain".equalsIgnoreCase(ct)) {
                                String text = textIdx >= 0 ? c.getString(textIdx) : null;
                                if (text == null) text = readMmsTextPart(cr, c.getLong(partIdIdx));
                                if (isValidMessageBody(text)) {
                                    StringBuilder sb = texts.get(id);
                                    if (sb == null) {
                                        sb = new StringBuilder();
                                        texts.put(id, sb);
                                    } else {
                                        sb.append('\n');
                                    }
                                    sb.append(text.trim());
                                }
                            } else if (ct != null && !mediaUris.containsKey(id)) {
                                long partId = c.getLong(partIdIdx);
                                mediaUris.put(id, ContentUris.withAppendedId(MMS_PART_URI, partId).toString());
                                mediaTypes.put(id, ct);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Resolve addresses (FROM for inbox, TO for sent/outbox)
        for (Long id : ids) {
            long[] m = meta.get(id);
            int box = (int) m[1];
            int addrType = (box == Telephony.Mms.MESSAGE_BOX_INBOX) ? 137 : 151;
            String addr = MmsHelper.getMmsAddress(context, id, addrType);
            if (addr == null && addrType == 137) {
                addr = MmsHelper.getMmsAddress(context, id, 151);
            }
            addresses.put(id, addr);
        }

        String placeholder = context.getString(R.string.mms_message);
        for (Long id : ids) {
            long[] m = meta.get(id);
            StringBuilder sb = texts.get(id);
            String mediaType = mediaTypes.get(id);
            String body;

            if (sb != null && sb.length() > 0) {
                body = sb.toString();
            } else if (isValidMessageBody(subjects.get(id))) {
                body = subjects.get(id).trim();
            } else if (mediaType != null) {
                if (mediaType.startsWith("image/")) {
                    body = context.getString(R.string.mms_photo);
                } else if (mediaType.startsWith("video/")) {
                    body = context.getString(R.string.mms_video);
                } else if (mediaType.startsWith("audio/")) {
                    body = context.getString(R.string.mms_audio);
                } else {
                    body = placeholder;
                }
            } else {
                body = placeholder;
            }

            int box = (int) m[1];
            int type = box == Telephony.Mms.MESSAGE_BOX_INBOX ? MessageItem.TYPE_INCOMING : box;
            MessageItem item = new MessageItem(id, threadId, addresses.get(id), body, m[0], type, 0, m[2] == 1);
            item.setMms(true);
            item.setAttachmentUri(mediaUris.get(id));
            item.setAttachmentType(mediaTypes.get(id));
            item.setSubject(subjects.get(id));
            Integer mType = mTypes.get(id);
            item.setDownloadPending(mType != null && mType == 130);
            if (item.hasAttachment()) {
                Log.d("MMS_DEBUG", "SmsHelper.loadMmsMessages: Returning MMS " + id
                        + " with attachmentUri=" + item.getAttachmentUri() + ", type=" + item.getAttachmentType());
            } else if (item.isDownloadPending()) {
                Log.d("MMS_DEBUG", "SmsHelper.loadMmsMessages: Returning MMS " + id + " marked as downloadPending");
            }
            result.add(item);
        }
        return result;
    }

    private static String readMmsTextPart(ContentResolver cr, long partId) {
        try (InputStream is = cr.openInputStream(ContentUris.withAppendedId(MMS_PART_URI, partId))) {
            if (is == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Conversation> getConversationsFallback(Context context, Set<Long> archivedSet, Set<Long> recycleBinSet) {
        List<Conversation> list = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Uri uri = Telephony.Sms.CONTENT_URI;
        String[] projection = new String[]{"thread_id", "address", "body", "date", "read"};

        Set<Long> processedThreads = new HashSet<>();
        try (Cursor cursor = cr.query(uri, projection, null, null, "date DESC")) {
            if (cursor != null) {
                int threadIdx = cursor.getColumnIndex("thread_id");
                int addrIdx = cursor.getColumnIndex("address");
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");
                int readIdx = cursor.getColumnIndex("read");

                while (cursor.moveToNext()) {
                    long threadId = cursor.getLong(threadIdx);
                    if (processedThreads.contains(threadId)) continue;
                    if (archivedSet.contains(threadId) || recycleBinSet.contains(threadId)) continue;

                    String body = cursor.getString(bodyIdx);
                    // Rows without text are not real text messages (e.g. empty drafts). Skip them so the thread
                    // uses its latest real message, and threads that hold only empty rows are not listed.
                    if (!isValidMessageBody(body)) continue;

                    processedThreads.add(threadId);
                    String address = cursor.getString(addrIdx);
                    long date = cursor.getLong(dateIdx);
                    boolean read = cursor.getInt(readIdx) == 1;

                    String name = ContactHelper.getContactName(context, address);
                    String photoUri = ContactHelper.getContactPhotoUri(context, address);

                    list.add(new Conversation(threadId, address, name, body, date, read, 1, photoUri));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static String getAddressForRecipientId(Context context, String recipientId) {
        if (recipientId == null || recipientId.trim().isEmpty()) return context != null ? context.getString(R.string.unknown) : "Unknown";
        String[] ids = recipientId.split(" ");
        String firstId = ids[0];

        Uri uri = Uri.parse("content://mms-sms/canonical-address/" + firstId);
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return recipientId;
    }

    /**
     * Loads the full canonical-address table (recipient id -> address) with a single query.
     * Returns an empty map if the provider does not expose it; callers then use the per-id lookup.
     */
    private static Map<String, String> loadCanonicalAddresses(ContentResolver cr) {
        Map<String, String> map = new HashMap<>();
        try (Cursor cursor = cr.query(CANONICAL_ADDRESSES_URI, new String[]{"_id", "address"}, null, null, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int addrIdx = cursor.getColumnIndex("address");
                if (idIdx >= 0 && addrIdx >= 0) {
                    while (cursor.moveToNext()) {
                        String address = cursor.getString(addrIdx);
                        if (address != null) {
                            map.put(String.valueOf(cursor.getLong(idIdx)), address);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * Resolves all recipients of a thread from the preloaded canonical-address map, supporting both
     * 1-on-1 and group MMS threads. Falls back to per-id lookup if needed.
     */
    private static String resolveRecipientAddress(Context context, String recipientIds, Map<String, String> canonicalAddresses) {
        if (recipientIds != null) {
            String trimmed = recipientIds.trim();
            if (!trimmed.isEmpty()) {
                String[] idArray = trimmed.split("\\s+");
                if (idArray.length > 1) {
                    StringBuilder addrs = new StringBuilder();
                    for (String id : idArray) {
                        String a = (canonicalAddresses != null) ? canonicalAddresses.get(id) : null;
                        if (a == null) a = getAddressForRecipientId(context, id);
                        if (a != null && !a.isEmpty()) {
                            if (addrs.length() > 0) addrs.append(", ");
                            addrs.append(a);
                        }
                    }
                    if (addrs.length() > 0) return addrs.toString();
                } else if (canonicalAddresses != null && canonicalAddresses.containsKey(idArray[0])) {
                    return canonicalAddresses.get(idArray[0]);
                }
            }
        }
        return getAddressForRecipientId(context, recipientIds);
    }

    /**
     * Finds the existing thread for a single address WITHOUT creating one.
     * Telephony.Threads.getOrCreateThreadId() inserts an empty thread row when none exists. When that is done
     * just to open a chat screen, the empty thread shows up as a blank conversation if nothing is sent.
     *
     * @return the thread id, or -1 if no thread exists yet for this address
     */
    public static long findExistingThreadId(Context context, String address) {
        if (address == null || address.trim().isEmpty()) return -1;
        ContentResolver cr = context.getContentResolver();

        Set<String> matchingIds = new HashSet<>();
        boolean canonicalAvailable = false;
        try (Cursor cursor = cr.query(CANONICAL_ADDRESSES_URI, new String[]{"_id", "address"}, null, null, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int addrIdx = cursor.getColumnIndex("address");
                if (idIdx >= 0 && addrIdx >= 0) {
                    canonicalAvailable = true;
                    while (cursor.moveToNext()) {
                        if (addressesMatch(context, address, cursor.getString(addrIdx))) {
                            matchingIds.add(String.valueOf(cursor.getLong(idIdx)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!canonicalAvailable) {
            // The provider does not expose canonical addresses: keep the previous lookup behaviour.
            return getOrCreateThreadIdOrInvalid(context, address);
        }
        if (matchingIds.isEmpty()) return -1;

        try (Cursor cursor = cr.query(CONVERSATIONS_URI, new String[]{"_id", "recipient_ids"}, null, null, "date DESC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int recipIdx = cursor.getColumnIndex("recipient_ids");
                if (idIdx >= 0 && recipIdx >= 0) {
                    while (cursor.moveToNext()) {
                        String recipientIds = cursor.getString(recipIdx);
                        // Single-recipient threads only, same as getOrCreateThreadId(context, address)
                        if (recipientIds != null && matchingIds.contains(recipientIds.trim())) {
                            return cursor.getLong(idIdx);
                        }
                    }
                    return -1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // The threads query is not available: keep the previous lookup behaviour.
        return getOrCreateThreadIdOrInvalid(context, address);
    }

    /**
     * Same lookup as getOrCreateThreadId(), but returns -1 on failure instead of a made-up id. Used by the SMS send
     * and receive paths: with -1 they leave thread_id out of the insert and the provider assigns the right thread
     * from the address. getOrCreateThreadId() returns System.currentTimeMillis() on failure, and a message stored
     * with that thread_id belongs to no existing conversation, so it never appeared anywhere.
     */
    public static long getOrCreateThreadIdOrInvalid(Context context, String address) {
        try {
            return Telephony.Threads.getOrCreateThreadId(context, address);
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "getOrCreateThreadIdOrInvalid: Thread lookup failed for " + maskAddress(address), e);
            return -1;
        }
    }

    /** Shortens a phone number or sender id for logs, so logs never contain full personal numbers. */
    public static String maskAddress(String address) {
        if (address == null) return "null";
        String a = address.trim();
        if (a.length() <= 3) return "***";
        return "***" + a.substring(a.length() - 3);
    }

    /**
     * Brings a conversation back from the recycle bin when a message is sent or received in it. The recycle bin
     * only hides the thread (its messages stay in the SMS database), so a new message in that thread was stored
     * but never shown in the conversation list, and emptying the bin later deleted it unseen.
     * Uses Room, so it must not be called on the main thread.
     */
    public static void restoreThreadFromRecycleBin(Context context, long threadId) {
        if (context == null || threadId <= 0) return;
        try {
            int removed = AppDatabase.getInstance(context).recycleBinDao().deleteByThreadId(threadId);
            if (removed > 0) {
                Log.i("SMS_DEBUG", "restoreThreadFromRecycleBin: New message in thread " + threadId + ", restored it from the recycle bin");
            }
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "restoreThreadFromRecycleBin: Failed for thread " + threadId, e);
        }
    }

    /** Same address-matching rules the telephony provider uses for threads: exact match, or phone-number match. */
    private static boolean addressesMatch(Context context, String address, String candidate) {
        if (address == null || candidate == null) return false;
        String a = address.trim();
        String b = candidate.trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equalsIgnoreCase(b)) return true;
        // Patterns.PHONE is the same check the provider uses internally (Telephony.Mms.isPhoneNumber is hidden API).
        return Patterns.PHONE.matcher(a).matches() && Patterns.PHONE.matcher(b).matches()
                && PhoneNumberUtils.compare(context, a, b);
    }

    public static List<MessageItem> getMessagesForThread(Context context, long threadId) {
        List<MessageItem> list = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Uri uri = Telephony.Sms.CONTENT_URI;
        String selection = "thread_id = ?";
        String[] selectionArgs = new String[]{String.valueOf(threadId)};
        // Only the columns that are used, instead of every column (projection null).
        String[] projection = new String[]{"_id", "address", "body", "date", "type", "status", "read"};

        try (Cursor cursor = cr.query(uri, projection, selection, selectionArgs, "date ASC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int addrIdx = cursor.getColumnIndex("address");
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");
                int typeIdx = cursor.getColumnIndex("type");
                int statusIdx = cursor.getColumnIndex("status");
                int readIdx = cursor.getColumnIndex("read");

                while (cursor.moveToNext()) {
                    String body = cursor.getString(bodyIdx);
                    // SMS rows without text are not real messages: SMS cannot carry attachments, and these rows
                    // come from empty drafts or blank inserts. They used to render as empty bubbles.
                    if (!isValidMessageBody(body)) continue;

                    long id = cursor.getLong(idIdx);
                    String address = cursor.getString(addrIdx);
                    long date = cursor.getLong(dateIdx);
                    int type = cursor.getInt(typeIdx);
                    int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : 0;
                    boolean read = readIdx >= 0 && cursor.getInt(readIdx) == 1;

                    list.add(new MessageItem(id, threadId, address, body, date, type, status, read));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static long getOrCreateThreadId(Context context, String address) {
        try {
            return Telephony.Threads.getOrCreateThreadId(context, address);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public static int getValidSmsSubscriptionId(Context context) {
        int subId = -1;
        try {
            subId = SubscriptionManager.getDefaultSmsSubscriptionId();
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: SubscriptionManager.getDefaultSmsSubscriptionId failed", e);
        }

        if (subId < 0 || subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                subId = SmsManager.getDefaultSmsSubscriptionId();
            } catch (Exception e) {
                Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: SmsManager.getDefaultSmsSubscriptionId failed", e);
            }
        }

        if (subId < 0 || subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            // No default SMS SIM is set (single SIM not reported, or "Ask every time" on dual SIM).
            try {
                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
                    if (list != null && !list.isEmpty()) {
                        subId = list.get(0).getSubscriptionId();
                        Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: No default SMS SIM set, using first active SIM (slot "
                                + list.get(0).getSimSlotIndex() + ", subId=" + subId + ", active SIMs=" + list.size() + ")");
                    } else {
                        Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: No default SMS SIM and no active SIM reported");
                    }
                }
            } catch (SecurityException e) {
                Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: No default SMS SIM and READ_PHONE_STATE is not granted, "
                        + "so the active SIMs cannot be listed. Android will choose the SIM.");
            } catch (Exception e) {
                Log.w("SMS_DEBUG", "getValidSmsSubscriptionId: Could not list active SIMs", e);
            }
        }
        return subId;
    }

    /**
     * True if the subscription belongs to a SIM that is currently active. A subscription id stays in old messages
     * after its SIM is removed, replaced or disabled, and sending through it fails.
     */
    private static boolean isActiveSubscription(Context context, int subId) {
        if (subId < 0 || subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Needs no permission. Returns INVALID_SIM_SLOT_INDEX when the subscription has no active SIM slot.
            return SubscriptionManager.getSlotIndex(subId) != SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        }
        try {
            SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            return sm == null || sm.getActiveSubscriptionInfo(subId) != null;
        } catch (Exception e) {
            // READ_PHONE_STATE not granted (Android 8-9): cannot check, so keep the previous behaviour and use it.
            return true;
        }
    }

    public static SmsManager getSmsManagerForContext(Context context) {
        return getSmsManagerForContext(context, getValidSmsSubscriptionId(context));
    }

    public static SmsManager getSmsManagerForContext(Context context, int subscriptionId) {
        if (subscriptionId < 0 || subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subscriptionId = getValidSmsSubscriptionId(context);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                SmsManager sm = context.getSystemService(SmsManager.class);
                if (sm != null) {
                    if (subscriptionId >= 0 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        return sm.createForSubscriptionId(subscriptionId);
                    }
                    return sm;
                }
            } catch (Exception e) {
                Log.w("SMS_DEBUG", "Failed to get SmsManager via getSystemService", e);
            }
        }
        if (subscriptionId >= 0 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
            } catch (Exception e) {
                Log.w("SMS_DEBUG", "Failed to get SmsManager for subId: " + subscriptionId, e);
            }
        }
        return SmsManager.getDefault();
    }

    /**
     * SIM (subscription) to reply with in a thread: the SIM of the newest received or successfully sent message,
     * if that SIM is still active. Returns -1 when none is usable; callers then use the default SMS SIM.
     *
     * Failed, outbox and queued rows are skipped. They used to be picked as well, and sendSms() stores the SIM on
     * every outgoing row, so one message that failed on a wrong or no longer active SIM made every later message in
     * the thread go out on that same SIM and fail again.
     */
    public static int getThreadSubscriptionId(Context context, long threadId) {
        if (threadId <= 0) return -1;
        // No "LIMIT" in the sort order: it is not part of the ContentProvider contract and a provider that enforces
        // strict SQL grammar rejects it. The exception was swallowed, so the thread's SIM would silently be ignored.
        try (Cursor c = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.SUBSCRIPTION_ID},
                "thread_id = ? AND sub_id >= 0 AND (type = ? OR type = ?)",
                new String[]{String.valueOf(threadId),
                        String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX),
                        String.valueOf(Telephony.Sms.MESSAGE_TYPE_SENT)},
                "date DESC")) {
            if (c != null) {
                Set<Integer> checked = new HashSet<>();
                int rows = 0;
                // Only the newest rows matter; stop early instead of walking a long conversation.
                while (c.moveToNext() && rows++ < 50) {
                    int subId = c.getInt(0);
                    if (subId < 0 || !checked.add(subId)) continue;
                    if (isActiveSubscription(context, subId)) {
                        return subId;
                    }
                    Log.w("SMS_DEBUG", "getThreadSubscriptionId: SIM subId=" + subId + " of thread " + threadId
                            + " is no longer active, not using it");
                }
            }
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "getThreadSubscriptionId: Could not read the SIM of thread " + threadId + ", using default SIM", e);
        }
        return -1;
    }

    public static boolean sendSms(Context context, String destination, String text) {
        return sendSms(context, destination, text, getValidSmsSubscriptionId(context));
    }

    public static boolean sendSms(Context context, String destination, String text, int subscriptionId) {
        if (destination == null || destination.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            Log.e("SMS_DEBUG", "sendSms: Destination or text is empty");
            return false;
        }

        // A group conversation's address is several numbers joined with ", " (see resolveRecipientAddress). The
        // cleaning below would merge them into one long invalid number, so the SMS would fail or reach a wrong
        // number. An SMS has exactly one recipient, so refuse instead of sending to a merged number.
        if (destination.indexOf(',') >= 0 || destination.indexOf(';') >= 0) {
            Log.e("SMS_DEBUG", "sendSms: Destination contains several recipients; a single SMS cannot be sent to it. Not sending.");
            return false;
        }

        // Clean destination number (keep + for international, strip whitespace, dashes, brackets)
        String cleanDestination = destination.trim();
        String digitsAndPlus = cleanDestination.replaceAll("[^0-9+*#]", "");
        if (!digitsAndPlus.isEmpty()) {
            cleanDestination = digitsAndPlus;
        }

        if (!isDefaultSmsApp(context)) {
            // Android lets a non-default app send, but stores the sent message itself and ignores this app's writes,
            // so the Outbox row below and the later status update have no effect.
            Log.w("SMS_DEBUG", "sendSms: App is not the default SMS app; Android stores the sent message, this app cannot update its status");
        }

        Uri insertedUri = null;
        try {
            SmsManager smsManager = getSmsManagerForContext(context, subscriptionId);
            Log.d("SMS_DEBUG", "sendSms: Sending to=" + maskAddress(cleanDestination) + ", subId=" + subscriptionId + ", length=" + text.length()
                    + ", phoneDefaultSmsSubId=" + SubscriptionManager.getDefaultSmsSubscriptionId());

            if (subscriptionId >= 0) {
                try {
                    SubscriptionManager subMgr = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (subMgr != null) {
                        SubscriptionInfo info = subMgr.getActiveSubscriptionInfo(subscriptionId);
                        if (info != null) {
                            Log.d("SMS_DEBUG", "sendSms: Routing via SIM slot " + info.getSimSlotIndex() + " (" + info.getCarrierName() + ")"
                                    + ", activeSims=" + subMgr.getActiveSubscriptionInfoCount());
                        }
                    }
                } catch (Exception e) {
                    Log.w("SMS_DEBUG", "sendSms: Could not read SIM details for subId=" + subscriptionId + " (" + e.getClass().getSimpleName() + ")");
                }
            }

            // -1 on failure (not a made-up id), so thread_id is left out below and the provider picks the thread.
            long threadId = getOrCreateThreadIdOrInvalid(context, cleanDestination);
            ArrayList<String> parts = smsManager.divideMessage(text);
            Log.d("SMS_DEBUG", "sendSms: Message divided into " + parts.size() + " parts");

            // Record message in Android SMS database with OUTBOX type while transmitting
            ContentValues values = new ContentValues();
            values.put("address", cleanDestination);
            values.put("body", text);
            values.put("date", System.currentTimeMillis());
            values.put("read", 1);
            values.put("seen", 1);
            values.put("type", Telephony.Sms.MESSAGE_TYPE_OUTBOX);
            if (threadId > 0) {
                values.put("thread_id", threadId);
            }
            if (subscriptionId >= 0 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                values.put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId);
            }

            try {
                insertedUri = context.getContentResolver().insert(Telephony.Sms.Outbox.CONTENT_URI, values);
            } catch (Exception e) {
                Log.w("SMS_DEBUG", "sendSms: Outbox insert failed, trying Sent box", e);
                try {
                    insertedUri = context.getContentResolver().insert(Telephony.Sms.Sent.CONTENT_URI, values);
                } catch (Exception e2) {
                    Log.e("SMS_DEBUG", "sendSms: Sent box insert failed too", e2);
                }
            }
            if (insertedUri == null) {
                Log.e("SMS_DEBUG", "sendSms: Outgoing message could not be stored; it is sent but will not appear or get a status");
            } else {
                Log.d("SMS_DEBUG", "sendSms: Stored outgoing message uri=" + insertedUri + ", threadId=" + threadId);
            }

            int baseReqCode = (int) (System.currentTimeMillis() & 0xffff);

            if (parts.size() <= 1) {
                Intent sentIntent = new Intent(context, SmsStatusReceiver.class);
                sentIntent.setAction(SmsStatusReceiver.ACTION_SMS_SENT);
                if (insertedUri != null) {
                    sentIntent.setData(insertedUri);
                    sentIntent.putExtra("message_uri", insertedUri);
                }
                PendingIntent sentPI = PendingIntent.getBroadcast(
                        context,
                        baseReqCode,
                        sentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                );

                // Note: passing null for deliveryIntent prevents carrier networks from rejecting SMS when delivery reports are not provisioned
                smsManager.sendTextMessage(cleanDestination, null, text, sentPI, null);
                Log.d("SMS_DEBUG", "sendSms: sendTextMessage executed successfully for single part");
            } else {
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();

                for (int i = 0; i < parts.size(); i++) {
                    Intent sentIntent = new Intent(context, SmsStatusReceiver.class);
                    sentIntent.setAction(SmsStatusReceiver.ACTION_SMS_SENT);
                    if (insertedUri != null) {
                        sentIntent.setData(insertedUri);
                        sentIntent.putExtra("message_uri", insertedUri);
                    }
                    PendingIntent sentPI = PendingIntent.getBroadcast(
                            context,
                            baseReqCode + (i * 2),
                            sentIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                    );
                    sentIntents.add(sentPI);
                }

                smsManager.sendMultipartTextMessage(cleanDestination, null, parts, sentIntents, null);
                Log.d("SMS_DEBUG", "sendSms: sendMultipartTextMessage executed successfully for " + parts.size() + " parts");
            }
            return true;
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                Log.e("SMS_DEBUG", "sendSms: Not allowed to send SMS (SEND_SMS permission missing or revoked)", e);
            } else {
                Log.e("SMS_DEBUG", "sendSms: Exception during SMS send", e);
            }
            if (insertedUri != null) {
                try {
                    ContentValues failedValues = new ContentValues();
                    failedValues.put("type", Telephony.Sms.MESSAGE_TYPE_FAILED);
                    failedValues.put("status", Telephony.Sms.STATUS_FAILED);
                    context.getContentResolver().update(insertedUri, failedValues, null, null);
                } catch (Exception e2) {
                    Log.e("SMS_DEBUG", "sendSms: Could not mark the message as failed", e2);
                }
            }
            return false;
        }
    }

    public static boolean retrySendSms(Context context, long messageId, String destination, String text) {
        // Retry on the conversation's SIM, like a normal send from the chat. It used to always use the default SIM.
        // Read before the failed row is deleted (getThreadSubscriptionId skips failed rows anyway).
        int threadSubId = getThreadSubscriptionId(context, getSmsThreadId(context, messageId));
        deleteSystemMessage(context, messageId);
        return threadSubId >= 0
                ? sendSms(context, destination, text, threadSubId)
                : sendSms(context, destination, text);
    }

    /** Thread id of one SMS, or -1. */
    private static long getSmsThreadId(Context context, long messageId) {
        try (Cursor c = context.getContentResolver().query(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                new String[]{Telephony.Sms.THREAD_ID}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception e) {
            Log.w("SMS_DEBUG", "getSmsThreadId: Could not read thread of message " + messageId, e);
        }
        return -1;
    }

    public static void markThreadAsRead(Context context, long threadId) {
        try {
            ContentValues values = new ContentValues();
            values.put("read", 1);
            values.put("seen", 1);
            context.getContentResolver().update(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values,
                    "thread_id = ? AND read = 0",
                    new String[]{String.valueOf(threadId)}
            );
            context.getContentResolver().update(
                    MMS_URI,
                    values,
                    "thread_id = ? AND read = 0",
                    new String[]{String.valueOf(threadId)}
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void markThreadAsUnread(Context context, long threadId) {
        try {
            ContentValues values = new ContentValues();
            values.put("read", 0);
            context.getContentResolver().update(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values,
                    "thread_id = ?",
                    new String[]{String.valueOf(threadId)}
            );
            context.getContentResolver().update(
                    MMS_URI,
                    values,
                    "thread_id = ?",
                    new String[]{String.valueOf(threadId)}
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteSystemThread(Context context, long threadId) {
        boolean deletedAny = false;
        try {
            Uri uri = Uri.parse("content://mms-sms/conversations/" + threadId);
            int count = context.getContentResolver().delete(uri, null, null);
            if (count > 0) deletedAny = true;
        } catch (Exception ignored) {}

        try {
            Uri uri = Uri.parse("content://sms/conversations/" + threadId);
            int count = context.getContentResolver().delete(uri, null, null);
            if (count > 0) deletedAny = true;
        } catch (Exception ignored) {}

        try {
            int count = context.getContentResolver().delete(MMS_URI, "thread_id = ?", new String[]{String.valueOf(threadId)});
            if (count > 0) deletedAny = true;
        } catch (Exception ignored) {}

        return deletedAny;
    }

    /** Deletes one MMS. MMS ids are a separate sequence from SMS ids, so deleteMessage() must not be used for them. */
    public static boolean deleteMmsMessage(Context context, long messageId) {
        try {
            int count = context.getContentResolver().delete(ContentUris.withAppendedId(MMS_URI, messageId), null, null);
            return count > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteSystemMessage(Context context, long messageId) {
        try {
            Uri uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId);
            int count = context.getContentResolver().delete(uri, null, null);
            return count > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String formatTimestamp(Context context, long timeMs) {
        if (timeMs <= 0) return "";
        Calendar msgTime = Calendar.getInstance();
        msgTime.setTimeInMillis(timeMs);

        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) {
            if (now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)) {
                return DateFormat.format("h:mm a", msgTime).toString();
            } else if (now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1) {
                return context != null ? context.getString(R.string.yesterday) : "Yesterday";
            } else {
                return DateFormat.format("MMM d", msgTime).toString();
            }
        } else {
            return DateFormat.format("MMM d, yyyy", msgTime).toString();
        }
    }

    public static String formatTimestamp(long timeMs) {
        return formatTimestamp(null, timeMs);
    }
}
