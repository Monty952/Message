package com.example.message.utils;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.message.R;
import com.example.message.database.AppDatabase;
import com.example.message.receivers.MmsStatusReceiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.View;
import android.widget.ImageView;

/**
 * Complete MMS management: PDU encoding, decoding, transmission, download,
 * and Telephony provider storage for image, video, audio and text attachments.
 */
public class MmsHelper {

    private static final String TAG = "MmsHelper";

    public static final Uri MMS_URI = Uri.parse("content://mms");
    public static final Uri MMS_PART_URI = Uri.parse("content://mms/part");
    public static final Uri MMS_INBOX_URI = Uri.parse("content://mms/inbox");
    public static final Uri MMS_OUTBOX_URI = Uri.parse("content://mms/outbox");
    public static final Uri MMS_SENT_URI = Uri.parse("content://mms/sent");

    // Standard WAP MMS PDU Header Keys (WAP-209-MMSEncapsulation)
    private static final int HDR_MESSAGE_TYPE = 0x8C;
    private static final int HDR_TRANSACTION_ID = 0x98;
    private static final int HDR_MMS_VERSION = 0x8D;
    private static final int HDR_DATE = 0x85;
    private static final int HDR_FROM = 0x89;
    private static final int HDR_TO = 0x97;
    private static final int HDR_SUBJECT = 0x96;
    private static final int HDR_CONTENT_TYPE = 0x84;
    private static final int HDR_CONTENT_LOCATION = 0x83; // X-Mms-Content-Location (WAP-209: 0x83)
    private static final int HDR_MESSAGE_CLASS = 0x8A;    // X-Mms-Message-Class (WAP-209: 0x8A)
    private static final int HDR_MESSAGE_SIZE = 0x8E;
    private static final int HDR_EXPIRY = 0x88;
    private static final int HDR_STATUS = 0x95;

    // Standard MMS Message Types
    public static final int MESSAGE_TYPE_SEND_REQ = 0x80;       // 128
    public static final int MESSAGE_TYPE_NOTIFICATION_IND = 0x82; // 130
    public static final int MESSAGE_TYPE_RETRIEVE_CONF = 0x84;  // 132

    // MMS Version 1.2
    private static final int MMS_VERSION_1_2 = 0x92;

    // Well-known content type for multipart/related
    private static final String CT_MULTIPART_RELATED = "application/vnd.wap.multipart.related";

    /**
     * Represents a single body part of an MMS.
     */
    public static class MmsPart {
        public String contentType;
        public String name;
        public String contentId;
        public String contentLocation;
        public byte[] data;

        public MmsPart(String contentType, String name, String contentId, String contentLocation, byte[] data) {
            this.contentType = contentType;
            this.name = name;
            this.contentId = contentId;
            this.contentLocation = contentLocation;
            this.data = data;
        }
    }

    /**
     * Parses WAP Push notification indication PDU and downloads the MMS.
     */
    public static void handleWapPush(Context context, Intent intent) {
        try {
            byte[] pushData = intent.getByteArrayExtra("data");
            if (pushData == null || pushData.length == 0) {
                Log.w("MMS_DEBUG", "MmsHelper.handleWapPush: pushData is null or empty");
                return;
            }

            int defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId();
            int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    intent.getIntExtra("subscription",
                    intent.getIntExtra("sub_id", defaultSubId)));

            Log.d("MMS_DEBUG", "MmsHelper.handleWapPush: Incoming MMS notification received. Size=" + pushData.length
                    + " bytes, SubId=" + subId);

            NotificationInd notification = parseNotificationInd(pushData);
            if (notification == null || TextUtils.isEmpty(notification.contentLocation)) {
                Log.e("MMS_DEBUG", "MmsHelper.handleWapPush: FAILED to extract contentLocation from WAP Push PDU! Aborting download.");
                return;
            }

            Log.d("MMS_DEBUG", "MmsHelper.handleWapPush: Extracted contentLocation=" + notification.contentLocation
                    + ", selected SubId=" + subId);

            String sender = notification.from != null ? notification.from : "Unknown";
            // Check if sender is blocked
            AppDatabase db = AppDatabase.getInstance(context);
            if (db.blockedNumberDao().isBlockedSync(sender)) {
                Log.i("MMS_DEBUG", "MmsHelper.handleWapPush: Ignoring incoming MMS from blocked sender");
                return;
            }

            long threadId = SmsHelper.getOrCreateThreadId(context, sender);

            // Record notification into content://mms/inbox
            ContentValues values = new ContentValues();
            values.put("thread_id", threadId);
            values.put("date", System.currentTimeMillis() / 1000L);
            values.put("msg_box", Telephony.Mms.MESSAGE_BOX_INBOX);
            values.put("read", 0);
            values.put("seen", 0);
            values.put("m_type", MESSAGE_TYPE_NOTIFICATION_IND);
            values.put("ct_l", notification.contentLocation);
            values.put("m_size", notification.messageSize);
            if (!TextUtils.isEmpty(notification.subject)) {
                values.put("sub", notification.subject);
            }
            if (!TextUtils.isEmpty(notification.transactionId)) {
                values.put("tr_id", notification.transactionId);
            }

            Uri mmsUri = context.getContentResolver().insert(MMS_INBOX_URI, values);
            long mmsId = -1;
            if (mmsUri != null) {
                mmsId = ContentUris.parseId(mmsUri);
                // Insert FROM address (type 137)
                insertAddress(context, mmsId, sender, 137);
                Log.d("MMS_DEBUG", "MmsHelper.handleWapPush: Inserted notification record into MMS inbox. id=" + mmsId + ", uri=" + mmsUri);
            } else {
                Log.w("MMS_DEBUG", "MmsHelper.handleWapPush: MMS inbox insert returned null Uri");
            }

            // Trigger immediate background download
            downloadMms(context, mmsId, threadId, sender, notification.contentLocation, subId);

        } catch (Exception e) {
            Log.e("MMS_DEBUG", "MmsHelper.handleWapPush: Error handling WAP push MMS", e);
        }
    }

    /**
     * Initiates MMS download via Android SmsManager.downloadMultimediaMessage.
     */
    public static void downloadMms(Context context, long mmsId, long threadId, String sender, String contentLocation, int subId) {
        if (TextUtils.isEmpty(contentLocation)) {
            Log.e("MMS_DEBUG", "MmsHelper.downloadMms: contentLocation is empty, aborting download");
            return;
        }
        try {
            File cacheDir = new File(context.getCacheDir(), "mms");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File downloadFile = new File(cacheDir, "mms_dl_" + (mmsId > 0 ? mmsId : UUID.randomUUID().toString()) + ".pdu");

            if (downloadFile.exists()) {
                downloadFile.delete();
            }
            downloadFile.createNewFile();
            downloadFile.setReadable(true, false);
            downloadFile.setWritable(true, false);

            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", downloadFile);

            // Explicitly grant write & read permissions to telephony services so SmsManager/MmsService can write PDU
            try {
                context.grantUriPermission("com.android.phone", contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            try {
                context.grantUriPermission("com.android.providers.telephony", contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            Intent downloadedIntent = new Intent(context, MmsStatusReceiver.class);
            downloadedIntent.setAction(MmsStatusReceiver.ACTION_MMS_DOWNLOADED);
            downloadedIntent.putExtra("mms_id", mmsId);
            downloadedIntent.putExtra("thread_id", threadId);
            downloadedIntent.putExtra("sender", sender);
            downloadedIntent.putExtra("file_path", downloadFile.getAbsolutePath());
            downloadedIntent.putExtra("content_location", contentLocation);
            downloadedIntent.putExtra("subscription_id", subId);

            int requestCode = (int) (mmsId > 0 ? mmsId : (System.currentTimeMillis() & 0xffff));
            PendingIntent downloadedPI = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    downloadedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            SmsManager smsManager = getSmsManager(context, subId);
            Log.d("MMS_DEBUG", "MmsHelper.downloadMms: Invoking SmsManager.downloadMultimediaMessage for MMS ID=" + mmsId
                    + ", SubId=" + subId + ", file=" + downloadFile.getAbsolutePath() + ", contentUri=" + contentUri);

            smsManager.downloadMultimediaMessage(context, contentLocation, contentUri, null, downloadedPI);
            Log.i("MMS_DEBUG", "MmsHelper.downloadMms: MMS download method invoked successfully");

        } catch (Exception e) {
            Log.e("MMS_DEBUG", "MmsHelper.downloadMms: Failed to initiate MMS download", e);
        }
    }

    /**
     * Processes the downloaded MMS PDU file, writes its parts to telephony database,
     * updates the MMS record, and raises a notification.
     */
    public static void processDownloadedMms(Context context, long mmsId, long threadId, String sender, String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            Log.w("MMS_DEBUG", "processDownloadedMms: Downloaded MMS file is empty or missing: " + filePath
                    + " (exists=" + file.exists() + ", size=" + (file.exists() ? file.length() : 0) + ")");
            return;
        }

        Log.d("MMS_DEBUG", "processDownloadedMms: Starting processing. File=" + filePath + ", size=" + file.length() + " bytes");

        try {
            byte[] pduBytes;
            try (FileInputStream fis = new FileInputStream(file)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    bos.write(buf, 0, read);
                }
                pduBytes = bos.toByteArray();
            }

            RetrieveConf conf = parseRetrieveConf(pduBytes);
            if (conf == null) {
                Log.e("MMS_DEBUG", "processDownloadedMms: PDU parsing failure! Could not parse RetrieveConf");
                return;
            }

            int partCount = conf.parts != null ? conf.parts.size() : 0;
            Log.d("MMS_DEBUG", "processDownloadedMms: PDU parsing success. Number of extracted MMS parts=" + partCount);

            ContentResolver cr = context.getContentResolver();
            String messageText = null;
            String firstMediaUri = null;
            String firstMediaType = null;

            if (mmsId <= 0) {
                // If notification row wasn't created earlier, create inbox row now
                String from = !TextUtils.isEmpty(conf.from) ? conf.from : sender;
                if (threadId <= 0 && !TextUtils.isEmpty(from)) {
                    threadId = SmsHelper.getOrCreateThreadId(context, from);
                }
                ContentValues cv = new ContentValues();
                cv.put("thread_id", threadId);
                cv.put("date", conf.date > 0 ? conf.date : (System.currentTimeMillis() / 1000L));
                cv.put("msg_box", Telephony.Mms.MESSAGE_BOX_INBOX);
                cv.put("read", 0);
                cv.put("seen", 0);
                cv.put("m_type", MESSAGE_TYPE_RETRIEVE_CONF);
                if (!TextUtils.isEmpty(conf.subject)) {
                    cv.put("sub", conf.subject);
                }
                Uri inserted = cr.insert(MMS_INBOX_URI, cv);
                if (inserted != null) {
                    mmsId = ContentUris.parseId(inserted);
                    insertAddress(context, mmsId, from, 137);
                    Log.d("MMS_DEBUG", "processDownloadedMms: Created new MMS inbox row id=" + mmsId);
                }
            } else {
                // Update existing row
                ContentValues cv = new ContentValues();
                cv.put("m_type", MESSAGE_TYPE_RETRIEVE_CONF);
                if (!TextUtils.isEmpty(conf.subject)) {
                    cv.put("sub", conf.subject);
                }
                if (conf.date > 0) {
                    cv.put("date", conf.date);
                }
                int updatedRows = cr.update(ContentUris.withAppendedId(MMS_URI, mmsId), cv, null, null);
                Log.d("MMS_DEBUG", "processDownloadedMms: Updated existing MMS row id=" + mmsId + ", rows=" + updatedRows);
            }

            // Save parts to local app storage AND to content://mms/<id>/part
            File mediaDir = new File(context.getFilesDir(), "mms_media");
            if (!mediaDir.exists()) mediaDir.mkdirs();

            if (mmsId > 0 && conf.parts != null) {
                int partIndex = 0;
                for (MmsPart part : conf.parts) {
                    if (part.contentType == null) continue;
                    if ("application/smil".equalsIgnoreCase(part.contentType)) continue;

                    partIndex++;
                    int partSize = part.data != null ? part.data.length : 0;
                    Log.d("MMS_DEBUG", "processDownloadedMms: Part #" + partIndex + ": contentType=" + part.contentType
                            + ", name=" + part.name + ", contentSize=" + partSize + " bytes");

                    File localMediaFile = null;
                    if (part.data != null && !"text/plain".equalsIgnoreCase(part.contentType)) {
                        String ext = getExtensionForMimeType(part.contentType);
                        localMediaFile = new File(mediaDir, "mms_" + mmsId + "_" + partIndex + ext);
                        try (FileOutputStream fos = new FileOutputStream(localMediaFile)) {
                            fos.write(part.data);
                            fos.flush();
                            Log.d("MMS_DEBUG", "processDownloadedMms: Local image file creation success at "
                                    + localMediaFile.getAbsolutePath() + " (" + localMediaFile.length() + " bytes)");
                        } catch (Exception e) {
                            Log.e("MMS_DEBUG", "processDownloadedMms: Local image file creation failed", e);
                        }
                    }

                    ContentValues pv = new ContentValues();
                    pv.put("mid", mmsId);
                    pv.put("ct", part.contentType);
                    if (part.name != null) pv.put("name", part.name);
                    if (part.contentLocation != null) pv.put("cl", part.contentLocation);
                    if (part.contentId != null) pv.put("cid", part.contentId);
                    if (localMediaFile != null) {
                        pv.put("_data", localMediaFile.getAbsolutePath());
                    }

                    if ("text/plain".equalsIgnoreCase(part.contentType) && part.data != null) {
                        String text = new String(part.data, StandardCharsets.UTF_8).trim();
                        pv.put("text", text);
                        if (messageText == null) {
                            messageText = text;
                        } else {
                            messageText += "\n" + text;
                        }
                    }

                    Uri partUri = null;
                    try {
                        partUri = cr.insert(Uri.parse("content://mms/" + mmsId + "/part"), pv);
                    } catch (Exception e) {
                        try {
                            partUri = cr.insert(MMS_PART_URI, pv);
                        } catch (Exception ignored) {}
                    }
                    Log.d("MMS_DEBUG", "processDownloadedMms: MMS part database insertion result: " + partUri);

                    if (partUri != null && part.data != null && !"text/plain".equalsIgnoreCase(part.contentType)) {
                        try (OutputStream os = cr.openOutputStream(partUri)) {
                            if (os != null) {
                                os.write(part.data);
                                os.flush();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (firstMediaUri == null && localMediaFile != null) {
                        firstMediaUri = Uri.fromFile(localMediaFile).toString();
                        firstMediaType = part.contentType;
                    } else if (firstMediaUri == null && partUri != null && !"text/plain".equalsIgnoreCase(part.contentType)) {
                        firstMediaUri = partUri.toString();
                        firstMediaType = part.contentType;
                    }
                }
            }

            Log.d("MMS_DEBUG", "processDownloadedMms: Final attachment URI for MMS ID " + mmsId
                    + ": firstMediaUri=" + firstMediaUri + ", firstMediaType=" + firstMediaType);

            // Notification display text
            String notifyText = messageText;
            if (TextUtils.isEmpty(notifyText)) {
                if (!TextUtils.isEmpty(conf.subject)) {
                    notifyText = conf.subject;
                } else if (firstMediaType != null) {
                    if (firstMediaType.startsWith("image/")) {
                        notifyText = context.getString(R.string.mms_photo);
                    } else if (firstMediaType.startsWith("video/")) {
                        notifyText = context.getString(R.string.mms_video);
                    } else if (firstMediaType.startsWith("audio/")) {
                        notifyText = context.getString(R.string.mms_audio);
                    } else {
                        notifyText = context.getString(R.string.mms_message);
                    }
                } else {
                    notifyText = context.getString(R.string.mms_message);
                }
            }

            String finalSender = !TextUtils.isEmpty(conf.from) ? conf.from : sender;
            NotificationHelper.showIncomingSmsNotification(context, finalSender, notifyText, threadId, null);

            // Cleanup temp download file
            file.delete();

        } catch (Exception e) {
            Log.e(TAG, "Error processing downloaded MMS PDU", e);
        }
    }

    /**
     * Sends an MMS containing subject, optional text, and attachment (image, video, or audio).
     */
    public static boolean sendMms(Context context, String recipient, String subject, String text, Uri attachmentUri, String mimeType, int subId) {
        if (TextUtils.isEmpty(recipient) && (TextUtils.isEmpty(text) && attachmentUri == null)) {
            return false;
        }

        try {
            long threadId = SmsHelper.getOrCreateThreadId(context, recipient);
            List<MmsPart> parts = new ArrayList<>();

            // 1. Text part
            if (!TextUtils.isEmpty(text)) {
                byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
                parts.add(new MmsPart("text/plain", "text.txt", "<text_0>", "text.txt", textBytes));
            }

            // 2. Attachment part
            if (attachmentUri != null && !TextUtils.isEmpty(mimeType)) {
                byte[] mediaData = readUriBytes(context, attachmentUri);
                if (mediaData != null && mediaData.length > 0) {
                    String ext = getExtensionForMimeType(mimeType);
                    String filename = "attachment_" + System.currentTimeMillis() + ext;
                    parts.add(new MmsPart(mimeType, filename, "<media_0>", filename, mediaData));
                }
            }

            // 3. SMIL layout part
            String smil = buildSmil(parts);
            parts.add(0, new MmsPart("application/smil", "smil.xml", "<smil>", "smil.xml", smil.getBytes(StandardCharsets.UTF_8)));

            // Build SendReq PDU
            byte[] pduBytes = encodeSendReq(recipient, subject, parts);

            // Write PDU to temp file for SmsManager
            File cacheDir = new File(context.getCacheDir(), "mms_out");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File pduFile = new File(cacheDir, "mms_send_" + System.currentTimeMillis() + ".pdu");
            try (FileOutputStream fos = new FileOutputStream(pduFile)) {
                fos.write(pduBytes);
                fos.flush();
            }

            Uri pduContentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pduFile);

            // Explicitly grant read permissions to telephony services so SmsManager/MmsService can read outgoing PDU
            try {
                context.grantUriPermission("com.android.phone", pduContentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            try {
                context.grantUriPermission("com.android.providers.telephony", pduContentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            // Insert into Outbox (Telephony.Mms.Outbox)
            ContentResolver cr = context.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put("thread_id", threadId);
            cv.put("date", System.currentTimeMillis() / 1000L);
            cv.put("msg_box", Telephony.Mms.MESSAGE_BOX_OUTBOX);
            cv.put("read", 1);
            cv.put("seen", 1);
            cv.put("m_type", MESSAGE_TYPE_SEND_REQ);
            if (!TextUtils.isEmpty(subject)) {
                cv.put("sub", subject);
            }

            Uri insertedMmsUri = cr.insert(MMS_OUTBOX_URI, cv);
            long mmsId = insertedMmsUri != null ? ContentUris.parseId(insertedMmsUri) : System.currentTimeMillis();

            // Insert TO address (type 151)
            if (insertedMmsUri != null) {
                insertAddress(context, mmsId, recipient, 151);

                // Save attachment in local app directory mms_media
                File mediaDir = new File(context.getFilesDir(), "mms_media");
                if (!mediaDir.exists()) mediaDir.mkdirs();
                if (attachmentUri != null && !TextUtils.isEmpty(mimeType)) {
                    byte[] mediaData = readUriBytes(context, attachmentUri);
                    if (mediaData != null && mediaData.length > 0) {
                        String ext = getExtensionForMimeType(mimeType);
                        File localMedia = new File(mediaDir, "mms_" + mmsId + "_1" + ext);
                        try (FileOutputStream fos = new FileOutputStream(localMedia)) {
                            fos.write(mediaData);
                            fos.flush();
                        } catch (Exception ignored) {}
                    }
                }

                // Insert parts into content://mms/<mmsId>/part
                for (MmsPart p : parts) {
                    ContentValues pv = new ContentValues();
                    pv.put("mid", mmsId);
                    pv.put("ct", p.contentType);
                    if (p.name != null) pv.put("name", p.name);
                    if (p.contentLocation != null) pv.put("cl", p.contentLocation);
                    if (p.contentId != null) pv.put("cid", p.contentId);
                    if ("text/plain".equalsIgnoreCase(p.contentType) && p.data != null) {
                        pv.put("text", new String(p.data, StandardCharsets.UTF_8));
                    }
                    Uri partUri = null;
                    try {
                        partUri = cr.insert(Uri.parse("content://mms/" + mmsId + "/part"), pv);
                    } catch (Exception e) {
                        try {
                            partUri = cr.insert(MMS_PART_URI, pv);
                        } catch (Exception ignored) {}
                    }
                    if (partUri != null && p.data != null && !"text/plain".equalsIgnoreCase(p.contentType)) {
                        try (OutputStream os = cr.openOutputStream(partUri)) {
                            if (os != null) {
                                os.write(p.data);
                                os.flush();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Create sent PendingIntent
            Intent sentIntent = new Intent(context, MmsStatusReceiver.class);
            sentIntent.setAction(MmsStatusReceiver.ACTION_MMS_SENT);
            sentIntent.putExtra("mms_id", mmsId);
            sentIntent.putExtra("thread_id", threadId);
            sentIntent.putExtra("file_path", pduFile.getAbsolutePath());

            PendingIntent sentPI = PendingIntent.getBroadcast(
                    context,
                    (int) (mmsId & 0xffff),
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            // Transmit via SmsManager
            SmsManager smsManager = getSmsManager(context, subId);
            smsManager.sendMultimediaMessage(context, pduContentUri, null, null, sentPI);
            Log.i(TAG, "MMS transmission queued successfully to: " + recipient);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send MMS", e);
            return false;
        }
    }

    public static boolean sendMms(Context context, String recipient, String text, Uri attachmentUri, String mimeType) {
        return sendMms(context, recipient, null, text, attachmentUri, mimeType, SubscriptionManager.getDefaultSmsSubscriptionId());
    }

    // ── Database Helpers ─────────────────────────────────────────────────────

    private static void insertAddress(Context context, long mmsId, String address, int type) {
        if (address == null || address.trim().isEmpty()) return;
        try {
            Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
            ContentValues cv = new ContentValues();
            cv.put("address", address.trim());
            cv.put("type", type);
            cv.put("charset", 106); // UTF-8
            context.getContentResolver().insert(addrUri, cv);
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert address into MMS", e);
        }
    }

    public static String getMmsAddress(Context context, long mmsId, int type) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        try (Cursor cursor = context.getContentResolver().query(addrUri,
                new String[]{"address"}, "type = ?", new String[]{String.valueOf(type)}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read address for MMS " + mmsId, e);
        }
        return null;
    }

    // ── PDU Encoding (M-Send-Req) ─────────────────────────────────────────────

    private static byte[] encodeSendReq(String recipient, String subject, List<MmsPart> parts) throws Exception {
        ByteArrayOutputStream headerStream = new ByteArrayOutputStream();

        // 1. X-Mms-Message-Type: m-send-req (0x80)
        headerStream.write(HDR_MESSAGE_TYPE);
        headerStream.write(MESSAGE_TYPE_SEND_REQ);

        // 2. X-Mms-Transaction-ID
        headerStream.write(HDR_TRANSACTION_ID);
        headerStream.write(("T" + System.currentTimeMillis()).getBytes(StandardCharsets.US_ASCII));
        headerStream.write(0x00);

        // 3. X-Mms-MMS-Version: 1.2
        headerStream.write(HDR_MMS_VERSION);
        headerStream.write(MMS_VERSION_1_2);

        // 4. Date
        headerStream.write(HDR_DATE);
        encodeLongInteger(headerStream, System.currentTimeMillis() / 1000L);

        // 5. From: insert-address-token
        headerStream.write(HDR_FROM);
        headerStream.write(0x01); // Length = 1
        headerStream.write(0x80); // Insert-address-token

        // 6. To
        String[] recipients = recipient.split("[,;]");
        for (String r : recipients) {
            String trimmed = r.trim();
            if (!trimmed.isEmpty()) {
                headerStream.write(HDR_TO);
                headerStream.write(encodeEncodedStringValue(trimmed));
            }
        }

        // 7. Subject (optional)
        if (!TextUtils.isEmpty(subject)) {
            headerStream.write(HDR_SUBJECT);
            headerStream.write(encodeEncodedStringValue(subject));
        }

        // 8. Content-Type: application/vnd.wap.multipart.related (0x33 | 0x80 = 0xB3)
        headerStream.write(HDR_CONTENT_TYPE);
        headerStream.write(0xB3); // Short integer for application/vnd.wap.multipart.related

        // Multipart Body
        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        encodeUintvar(bodyStream, parts.size()); // Number of parts

        for (MmsPart part : parts) {
            ByteArrayOutputStream partHeaderStream = new ByteArrayOutputStream();

            // Content-Type
            encodeContentType(partHeaderStream, part.contentType);

            // Content-Location
            if (part.contentLocation != null) {
                partHeaderStream.write(0x8E); // Content-Location header
                partHeaderStream.write(part.contentLocation.getBytes(StandardCharsets.US_ASCII));
                partHeaderStream.write(0x00);
            }

            // Content-ID
            if (part.contentId != null) {
                partHeaderStream.write(0xC0); // Content-ID header
                partHeaderStream.write(part.contentId.getBytes(StandardCharsets.US_ASCII));
                partHeaderStream.write(0x00);
            }

            byte[] partHeaders = partHeaderStream.toByteArray();
            byte[] partData = part.data != null ? part.data : new byte[0];

            encodeUintvar(bodyStream, partHeaders.length);
            encodeUintvar(bodyStream, partData.length);
            bodyStream.write(partHeaders);
            bodyStream.write(partData);
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(headerStream.toByteArray());
        result.write(bodyStream.toByteArray());
        return result.toByteArray();
    }

    // ── PDU Decoding (M-Notification-Ind & M-Retrieve-Conf) ───────────────────

    public static class NotificationInd {
        public String transactionId;
        public String contentLocation;
        public String from;
        public String subject;
        public long messageSize;
        public long expiry;
    }

    public static class RetrieveConf {
        public String from;
        public String subject;
        public long date;
        public List<MmsPart> parts = new ArrayList<>();
    }

    public static NotificationInd parseNotificationInd(byte[] data) {
        if (data == null || data.length == 0) return null;
        NotificationInd ind = new NotificationInd();
        ByteArrayInputStream is = new ByteArrayInputStream(data);

        while (is.available() > 0) {
            int header = is.read();
            if (header == -1) break;

            switch (header) {
                case HDR_TRANSACTION_ID:
                    ind.transactionId = readNullTerminatedString(is);
                    break;
                case HDR_CONTENT_LOCATION: // 0x83
                    ind.contentLocation = readNullTerminatedString(is);
                    break;
                case HDR_FROM:
                    ind.from = readEncodedAddress(is);
                    break;
                case HDR_SUBJECT:
                    ind.subject = readEncodedStringValue(is);
                    break;
                case HDR_MESSAGE_SIZE:
                    ind.messageSize = readLongInteger(is);
                    break;
                case HDR_EXPIRY:
                    int expLen = readValueLength(is);
                    for (int k = 0; k < expLen; k++) is.read();
                    break;
                case HDR_MESSAGE_CLASS: // 0x8A
                    skipHeaderValue(is, header);
                    break;
                default:
                    skipHeaderValue(is, header);
                    break;
            }
        }

        // Bulletproof URL recovery: If contentLocation was not parsed via headers,
        // scan raw data for "http://" or "https://"
        if (TextUtils.isEmpty(ind.contentLocation)) {
            ind.contentLocation = extractUrlFromRawPdu(data);
            if (!TextUtils.isEmpty(ind.contentLocation)) {
                Log.d("MMS_DEBUG", "MmsHelper: Extracted contentLocation via raw byte scan fallback: " + ind.contentLocation);
            }
        }

        Log.d("MMS_DEBUG", "MmsHelper.parseNotificationInd: Result=" + (ind != null ? "SUCCESS" : "FAIL")
                + ", contentLocation=" + ind.contentLocation
                + ", hasFrom=" + (ind.from != null)
                + ", messageSize=" + ind.messageSize);

        return ind;
    }

    private static String extractUrlFromRawPdu(byte[] data) {
        if (data == null || data.length < 8) return null;
        String s = new String(data, StandardCharsets.ISO_8859_1);
        int idx = s.indexOf("http://");
        if (idx == -1) {
            idx = s.indexOf("https://");
        }
        if (idx != -1) {
            int end = idx;
            while (end < s.length()) {
                char c = s.charAt(end);
                if (c <= 0x20 || c == 0x7F || c == '"' || c == '<' || c == '>' || c == '\'') {
                    break;
                }
                end++;
            }
            if (end > idx + 8) {
                return s.substring(idx, end).trim();
            }
        }
        return null;
    }

    public static RetrieveConf parseRetrieveConf(byte[] data) {
        if (data == null || data.length == 0) return null;
        RetrieveConf conf = new RetrieveConf();
        ByteArrayInputStream is = new ByteArrayInputStream(data);

        // Read headers until Content-Type
        while (is.available() > 0) {
            int header = is.read();
            if (header == -1) break;

            if (header == HDR_FROM) {
                conf.from = readEncodedAddress(is);
            } else if (header == HDR_SUBJECT) {
                conf.subject = readEncodedStringValue(is);
            } else if (header == HDR_DATE) {
                conf.date = readLongInteger(is);
            } else if (header == HDR_CONTENT_TYPE) {
                // Content type ends the header section in WAP-209
                parseContentTypeValue(is);
                break;
            } else {
                skipHeaderValue(is, header);
            }
        }

        // Read body parts
        if (is.available() > 0) {
            int nparts = readUintvar(is);
            for (int i = 0; i < nparts && is.available() > 0; i++) {
                int headerLen = readUintvar(is);
                int dataLen = readUintvar(is);

                byte[] headerBytes = new byte[headerLen];
                int hRead = 0;
                while (hRead < headerLen) {
                    int r = is.read(headerBytes, hRead, headerLen - hRead);
                    if (r == -1) break;
                    hRead += r;
                }

                ByteArrayInputStream hIs = new ByteArrayInputStream(headerBytes);
                String contentType = parseContentTypeValue(hIs);
                String contentLocation = null;
                String contentId = null;

                while (hIs.available() > 0) {
                    int partHdr = hIs.read();
                    if (partHdr == -1) break;
                    if (partHdr == 0x8E) {
                        contentLocation = readNullTerminatedString(hIs);
                    } else if (partHdr == 0xC0) {
                        contentId = readNullTerminatedString(hIs);
                    } else if ((partHdr & 0x80) != 0) {
                        skipHeaderValue(hIs, partHdr);
                    } else {
                        readNullTerminatedString(hIs);
                    }
                }

                byte[] partData = new byte[dataLen];
                int totalRead = 0;
                while (totalRead < dataLen) {
                    int r = is.read(partData, totalRead, dataLen - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }

                String name = contentLocation != null ? contentLocation : "part_" + i;
                conf.parts.add(new MmsPart(contentType, name, contentId, contentLocation, partData));
            }
        }
        if (conf.parts.isEmpty()) {
            recoverImagePartFallback(data, conf);
        }
        return conf;
    }

    private static void recoverImagePartFallback(byte[] data, RetrieveConf conf) {
        if (data == null || conf == null || !conf.parts.isEmpty()) return;
        // Check for JPEG (FF D8 FF ... FF D9)
        for (int i = 0; i < data.length - 4; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8 && (data[i + 2] & 0xFF) == 0xFF) {
                int eoi = -1;
                for (int j = data.length - 2; j > i + 2; j--) {
                    if ((data[j] & 0xFF) == 0xFF && (data[j + 1] & 0xFF) == 0xD9) {
                        eoi = j + 2;
                        break;
                    }
                }
                if (eoi != -1 && (eoi - i) > 100) {
                    byte[] imgBytes = Arrays.copyOfRange(data, i, eoi);
                    Log.d("MMS_DEBUG", "RetrieveConf: Recovered JPEG image (" + imgBytes.length + " bytes) via signature scan fallback");
                    conf.parts.add(new MmsPart("image/jpeg", "image.jpg", "<image_0>", "image.jpg", imgBytes));
                    return;
                }
            }
        }
        // Check for PNG (89 50 4E 47 0D 0A 1A 0A)
        for (int i = 0; i < data.length - 8; i++) {
            if ((data[i] & 0xFF) == 0x89 && data[i + 1] == 'P' && data[i + 2] == 'N' && data[i + 3] == 'G') {
                int end = data.length;
                for (int j = i + 8; j < data.length - 4; j++) {
                    if (data[j] == 'I' && data[j + 1] == 'E' && data[j + 2] == 'N' && data[j + 3] == 'D') {
                        end = Math.min(data.length, j + 8);
                        break;
                    }
                }
                byte[] imgBytes = Arrays.copyOfRange(data, i, end);
                Log.d("MMS_DEBUG", "RetrieveConf: Recovered PNG image (" + imgBytes.length + " bytes) via signature scan fallback");
                conf.parts.add(new MmsPart("image/png", "image.png", "<image_0>", "image.png", imgBytes));
                return;
            }
        }
    }

    // ── Low-Level Encoding / Decoding Utilities ──────────────────────────────

    private static void encodeUintvar(OutputStream os, long val) throws Exception {
        int[] buf = new int[8];
        int i = 0;
        buf[i++] = (int) (val & 0x7F);
        val >>>= 7;
        while (val > 0) {
            buf[i++] = (int) ((val & 0x7F) | 0x80);
            val >>>= 7;
        }
        for (int j = i - 1; j >= 0; j--) {
            os.write(buf[j]);
        }
    }

    private static int readUintvar(InputStream is) {
        int result = 0;
        int b;
        try {
            while ((b = is.read()) != -1) {
                result = (result << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) break;
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static void encodeLongInteger(OutputStream os, long val) throws Exception {
        byte[] bytes = new byte[8];
        int len = 0;
        for (int i = 7; i >= 0; i--) {
            byte b = (byte) ((val >>> (i * 8)) & 0xFF);
            if (len > 0 || b != 0 || i == 0) {
                bytes[len++] = b;
            }
        }
        os.write(len);
        os.write(bytes, 0, len);
    }

    private static long readLongInteger(InputStream is) {
        try {
            int len = is.read();
            if (len <= 0 || len > 8) return 0;
            long val = 0;
            for (int i = 0; i < len; i++) {
                int b = is.read();
                val = (val << 8) | (b & 0xFF);
            }
            return val;
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] encodeEncodedStringValue(String str) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] utf8 = str.getBytes(StandardCharsets.UTF_8);
        encodeUintvar(baos, utf8.length + 1);
        baos.write(0x6A); // Charset UTF-8 (MIBEnum 106)
        baos.write(utf8);
        return baos.toByteArray();
    }

    private static String readEncodedStringValue(InputStream is) {
        try {
            int len = readValueLength(is);
            if (len <= 0) return null;
            int charset = is.read();
            byte[] strBytes = new byte[Math.max(0, len - 1)];
            is.read(strBytes);
            return new String(strBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readEncodedAddress(InputStream is) {
        try {
            int len = readValueLength(is);
            if (len <= 0) return null;
            int token = is.read();
            if (token == 0x81) {
                // Insert-address-token: anonymous / no address provided
                for (int i = 0; i < len - 1; i++) is.read();
                return null;
            }
            // token == 0x80 (Address-present-token) or direct address character
            byte[] addrBytes = new byte[Math.max(0, len - 1)];
            int totalRead = 0;
            while (totalRead < addrBytes.length) {
                int r = is.read(addrBytes, totalRead, addrBytes.length - totalRead);
                if (r == -1) break;
                totalRead += r;
            }

            int start = 0;
            while (start < addrBytes.length && (addrBytes[start] & 0xFF) < 0x20) {
                start++;
            }
            if (start < addrBytes.length && (addrBytes[start] & 0xFF) >= 0x80) {
                start++;
            }
            int end = start;
            while (end < addrBytes.length && addrBytes[end] != 0x00) {
                end++;
            }
            if (start >= end) return null;

            String addr = new String(addrBytes, start, end - start, StandardCharsets.UTF_8).trim();
            if (addr.endsWith("/TYPE=PLMN")) {
                addr = addr.substring(0, addr.length() - "/TYPE=PLMN".length());
            } else if (addr.contains("/TYPE=")) {
                int idx = addr.indexOf("/TYPE=");
                addr = addr.substring(0, idx);
            }
            return addr.isEmpty() ? null : addr;
        } catch (Exception e) {
            return null;
        }
    }

    private static int readValueLength(InputStream is) {
        try {
            int b = is.read();
            if (b < 0x1F) return b;
            if (b == 0x1F) return readUintvar(is);
            return b & 0x7F;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String readNullTerminatedString(InputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        try {
            while ((b = is.read()) != -1 && b != 0x00) {
                baos.write(b);
            }
            return new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        } catch (Exception e) {
            return "";
        }
    }

    private static void encodeContentType(OutputStream os, String ct) throws Exception {
        if ("text/plain".equalsIgnoreCase(ct)) {
            os.write(0x03 | 0x80);
        } else if ("image/jpeg".equalsIgnoreCase(ct)) {
            os.write(0x3E | 0x80);
        } else if ("image/gif".equalsIgnoreCase(ct)) {
            os.write(0x1D | 0x80);
        } else if ("image/png".equalsIgnoreCase(ct)) {
            os.write(0x3F | 0x80);
        } else {
            os.write(ct.getBytes(StandardCharsets.US_ASCII));
            os.write(0x00);
        }
    }

    private static final ExecutorService MEDIA_EXECUTOR = Executors.newFixedThreadPool(4);

    public static ExecutorService getExecutor() {
        return MEDIA_EXECUTOR;
    }

    public static String getWellKnownContentType(int code) {
        switch (code) {
            case 0x03: return "text/plain";
            case 0x06: return "text/x-vCalendar";
            case 0x07: return "text/x-vCard";
            case 0x1D: return "image/gif";
            case 0x1E:
            case 0x3E: return "image/jpeg";
            case 0x20:
            case 0x3F: return "image/png";
            case 0x21: return "image/vnd.wap.wbmp";
            case 0x33: return "application/vnd.wap.multipart.related";
            default: return null;
        }
    }

    private static String parseContentTypeValue(InputStream is) {
        try {
            int b = is.read();
            if (b == -1) return "application/octet-stream";

            int length = 0;
            if (b < 0x1F) {
                length = b;
            } else if (b == 0x1F) {
                length = readUintvar(is);
            }

            if (length > 0) {
                // Value-length: length octets of content type and parameters follow
                byte[] ctBytes = new byte[length];
                int total = 0;
                while (total < length) {
                    int r = is.read(ctBytes, total, length - total);
                    if (r == -1) break;
                    total += r;
                }
                if (ctBytes.length > 0) {
                    int first = ctBytes[0] & 0xFF;
                    if ((first & 0x80) != 0) {
                        String wk = getWellKnownContentType(first & 0x7F);
                        if (wk != null) return wk;
                    } else if (first >= 0x20) {
                        int end = 0;
                        while (end < ctBytes.length && ctBytes[end] != 0) {
                            end++;
                        }
                        return new String(ctBytes, 0, end, StandardCharsets.US_ASCII);
                    }
                }
                return "application/vnd.wap.multipart.related";
            }

            // Short integer (1 byte)
            if ((b & 0x80) != 0) {
                String wk = getWellKnownContentType(b & 0x7F);
                return wk != null ? wk : "application/octet-stream";
            }

            // Null-terminated text string
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(b);
            while ((b = is.read()) != -1 && b != 0x00) {
                baos.write(b);
            }
            return new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private static String readContentType(InputStream is) {
        return parseContentTypeValue(is);
    }

    private static void skipHeaderValue(InputStream is, int header) {
        try {
            int b = is.read();
            if (b == -1) return;
            if (b < 0x1F) {
                // Short length: b octets follow
                int len = b;
                while (len > 0) {
                    long s = is.skip(len);
                    if (s <= 0) {
                        if (is.read() == -1) break;
                        len--;
                    } else {
                        len -= s;
                    }
                }
            } else if (b == 0x1F) {
                int len = readUintvar(is);
                while (len > 0) {
                    long s = is.skip(len);
                    if (s <= 0) {
                        if (is.read() == -1) break;
                        len--;
                    } else {
                        len -= s;
                    }
                }
            } else if ((b & 0x80) == 0) {
                // String ending in 0x00
                while ((b = is.read()) != -1 && b != 0x00);
            }
            // If (b & 0x80) != 0: 1-byte short integer, already consumed!
        } catch (Exception ignored) {}
    }

    public static File findSavedMediaFile(Context context, long mmsId) {
        if (mmsId <= 0) return null;
        File mediaDir = new File(context.getFilesDir(), "mms_media");
        if (!mediaDir.exists() || !mediaDir.isDirectory()) return null;
        File[] files = mediaDir.listFiles((dir, name) -> name.startsWith("mms_" + mmsId + "_"));
        if (files != null && files.length > 0) {
            for (File f : files) {
                if (f.length() > 0) return f;
            }
        }
        return null;
    }

    public static File getCachedPartFile(Context context, String uriStr) {
        if (uriStr == null || uriStr.isEmpty()) return null;
        if (uriStr.startsWith("file://") || uriStr.startsWith("/")) {
            File f = uriStr.startsWith("file://") ? new File(Uri.parse(uriStr).getPath()) : new File(uriStr);
            if (f.exists() && f.length() > 0) return f;
        }
        long partId = -1;
        try {
            partId = ContentUris.parseId(Uri.parse(uriStr));
        } catch (Exception ignored) {}
        File cacheDir = new File(context.getCacheDir(), "mms_media_cache");
        File cached = new File(cacheDir, "part_" + (partId > 0 ? partId : Math.abs(uriStr.hashCode())) + ".jpg");
        if (cached.exists() && cached.length() > 0) {
            return cached;
        }
        return null;
    }

    public static void loadMmsImage(Context context, String uriStr, ImageView imageView) {
        if (uriStr == null || uriStr.trim().isEmpty()) {
            Log.w("MMS_DEBUG", "loadMmsImage: uriStr is null or empty");
            imageView.setVisibility(View.GONE);
            return;
        }

        Log.d("MMS_DEBUG", "loadMmsImage: Loading MMS image for URI: " + uriStr);
        imageView.setVisibility(View.VISIBLE);
        Uri uri = Uri.parse(uriStr);

        // Case 1: Direct file
        if ("file".equalsIgnoreCase(uri.getScheme()) || uriStr.startsWith("/")) {
            File f = "file".equalsIgnoreCase(uri.getScheme()) ? new File(uri.getPath()) : new File(uriStr);
            if (f.exists() && f.length() > 0) {
                Log.d("MMS_DEBUG", "loadMmsImage: Case 1 - Direct file load success (" + f.length() + " bytes)");
                Glide.with(context)
                        .load(f)
                        .centerCrop()
                        .placeholder(R.drawable.bg_avatar_circle)
                        .into(imageView);
                return;
            }
        }

        // Case 2: Already cached
        File cachedFile = getCachedPartFile(context, uriStr);
        if (cachedFile != null) {
            Log.d("MMS_DEBUG", "loadMmsImage: Case 2 - Cached file load success (" + cachedFile.length() + " bytes)");
            Glide.with(context)
                    .load(cachedFile)
                    .centerCrop()
                    .placeholder(R.drawable.bg_avatar_circle)
                    .into(imageView);
            return;
        }

        // Case 3: Content URI - stream off-thread into cache file
        imageView.setImageResource(R.drawable.bg_avatar_circle);
        imageView.setTag(uriStr);

        MEDIA_EXECUTOR.execute(() -> {
            File resultFile = null;
            try {
                long partId = -1;
                try {
                    partId = ContentUris.parseId(uri);
                } catch (Exception ignored) {}

                File cacheDir = new File(context.getCacheDir(), "mms_media_cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File target = new File(cacheDir, "part_" + (partId > 0 ? partId : Math.abs(uriStr.hashCode())) + ".jpg");

                // Try reading stream from ContentResolver
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        try (FileOutputStream fos = new FileOutputStream(target)) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = is.read(buf)) != -1) {
                                fos.write(buf, 0, r);
                            }
                            fos.flush();
                        }
                        if (target.exists() && target.length() > 0) {
                            resultFile = target;
                        }
                    }
                } catch (Exception e) {
                    Log.w("MMS_DEBUG", "Could not open InputStream for " + uriStr, e);
                }

                // Fallback: If openInputStream failed, check if we have a saved file in mms_media
                if (resultFile == null && partId > 0) {
                    long mid = -1;
                    try (Cursor c = context.getContentResolver().query(uri, new String[]{"mid", "_data"}, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            mid = c.getLong(0);
                            int dataIdx = c.getColumnIndex("_data");
                            String dataPath = dataIdx >= 0 ? c.getString(dataIdx) : null;
                            if (dataPath != null && !dataPath.isEmpty()) {
                                File df = new File(dataPath);
                                if (df.exists() && df.length() > 0) {
                                    resultFile = df;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (resultFile == null && mid > 0) {
                        File saved = findSavedMediaFile(context, mid);
                        if (saved != null && saved.exists() && saved.length() > 0) {
                            resultFile = saved;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("MMS_DEBUG", "Error resolving MMS image: " + uriStr, e);
            }

            final File fileToLoad = resultFile;
            Log.d("MMS_DEBUG", "loadMmsImage: Case 3 - Off-thread resolution result: "
                    + (resultFile != null ? "SUCCESS (" + resultFile.length() + " bytes at " + resultFile.getAbsolutePath() + ")" : "FALLBACK_DIRECT_URI"));

            imageView.post(() -> {
                Object currentTag = imageView.getTag();
                if (uriStr.equals(currentTag)) {
                    if (fileToLoad != null && fileToLoad.exists() && fileToLoad.length() > 0) {
                        Log.d("MMS_DEBUG", "loadMmsImage: Image loading success into ImageView from resolved file");
                        Glide.with(context)
                                .load(fileToLoad)
                                .centerCrop()
                                .placeholder(R.drawable.bg_avatar_circle)
                                .into(imageView);
                    } else {
                        Log.d("MMS_DEBUG", "loadMmsImage: Image loading fallback to direct URI: " + uri);
                        Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .placeholder(R.drawable.bg_avatar_circle)
                                .into(imageView);
                    }
                }
            });
        });
    }

    private static String buildSmil(List<MmsPart> parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<smil><head><layout><root-layout width=\"100%\" height=\"100%\"/>");
        sb.append("<region id=\"Image\" width=\"100%\" height=\"70%\" fit=\"meet\"/>");
        sb.append("<region id=\"Text\" top=\"70%\" width=\"100%\" height=\"30%\"/>");
        sb.append("</layout></head><body><par dur=\"5000ms\">");

        for (MmsPart p : parts) {
            if (p.contentType == null) continue;
            if (p.contentType.startsWith("image/")) {
                sb.append("<img src=\"").append(p.name).append("\" region=\"Image\"/>");
            } else if (p.contentType.startsWith("video/")) {
                sb.append("<video src=\"").append(p.name).append("\" region=\"Image\"/>");
            } else if (p.contentType.startsWith("audio/")) {
                sb.append("<audio src=\"").append(p.name).append("\"/>");
            } else if (p.contentType.startsWith("text/")) {
                sb.append("<text src=\"").append(p.name).append("\" region=\"Text\"/>");
            }
        }
        sb.append("</par></body></smil>");
        return sb.toString();
    }

    private static byte[] readUriBytes(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read attachment URI bytes: " + uri, e);
            return null;
        }
    }

    private static String getExtensionForMimeType(String mime) {
        if (mime == null) return ".bin";
        switch (mime.toLowerCase()) {
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "video/mp4": return ".mp4";
            case "video/3gpp": return ".3gp";
            case "audio/amr": return ".amr";
            case "audio/mp3":
            case "audio/mpeg": return ".mp3";
            case "audio/aac": return ".aac";
            case "audio/m4a": return ".m4a";
            default: return ".dat";
        }
    }

    private static SmsManager getSmsManager(Context context, int subId) {
        if (subId >= 0) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SmsManager sm = context.getSystemService(SmsManager.class);
                    if (sm != null) {
                        return sm.createForSubscriptionId(subId);
                    }
                }
                return SmsManager.getSmsManagerForSubscriptionId(subId);
            } catch (Exception ignored) {}
        }
        return SmsHelper.getSmsManagerForContext(context);
    }
}
