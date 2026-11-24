package com.example.message.receivers;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

import androidx.core.app.RemoteInput;

import com.example.message.R;
import com.example.message.activities.PopupMessageActivity;
import com.example.message.database.AppDatabase;
import com.example.message.preferences.PreferenceManager;
import com.example.message.utils.NotificationHelper;
import com.example.message.utils.OtpHelper;
import com.example.message.utils.SmsHelper;

import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    public static void handleReply(Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence(NotificationHelper.KEY_TEXT_REPLY);
            String address = intent.getStringExtra("address");
            if (replyText != null && address != null) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    SmsHelper.sendSms(context, address, replyText.toString());
                });
            }
        }
    }

    public static void handleCopyOtp(Context context, Intent intent) {
        String otpCode = intent.getStringExtra(NotificationHelper.EXTRA_OTP_CODE);
        if (otpCode != null && !otpCode.trim().isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("OTP Code", otpCode.trim());
                clipboard.setPrimaryClip(clip);
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(context, context.getString(R.string.otp_copied, otpCode.trim()), Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * True if the inbox already holds this exact message: same sender, same network timestamp and same text.
     * The network timestamp identifies a delivery, so only a redelivered copy matches (not a new message).
     */
    private static boolean isAlreadyStored(Context context, String address, String body, long dateSent) {
        if (dateSent <= 0) return false;
        try (android.database.Cursor c = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{"_id"}, "address = ? AND date_sent = ? AND body = ?",
                new String[]{address, String.valueOf(dateSent), body}, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            android.util.Log.w("SMS_DEBUG", "SmsReceiver: Duplicate check failed, storing the message anyway", e);
            return false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        // Handle inline notification reply
        if ("com.example.message.ACTION_REPLY".equals(action)) {
            handleReply(context, intent);
            return;
        }

        // Handle Copy OTP button from notification
        if (NotificationHelper.ACTION_COPY_OTP.equals(action)) {
            handleCopyOtp(context, intent);
            return;
        }

        // Handle incoming SMS
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action) || Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            // When this app is the default SMS app, Android broadcasts every incoming SMS twice:
            // SMS_DELIVER (to the default app only) and SMS_RECEIVED (to every app). Both were handled, so each
            // message was written to the SMS database twice, with two notifications. The default app stores the
            // message from SMS_DELIVER only. When not default, SMS_RECEIVED still shows the notification as before.
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action) && SmsHelper.isDefaultSmsApp(context)) {
                return;
            }

            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) return;

            // A PDU that fails to parse comes back as a null entry. Use the first valid part for sender and time.
            SmsMessage firstPart = null;
            for (SmsMessage msg : messages) {
                if (msg != null) {
                    firstPart = msg;
                    break;
                }
            }
            if (firstPart == null) return;

            StringBuilder fullMessage = new StringBuilder();
            String sender = firstPart.getDisplayOriginatingAddress();
            if (sender == null || sender.trim().isEmpty()) {
                sender = firstPart.getOriginatingAddress();
            }
            long timestamp = firstPart.getTimestampMillis();
            int subId = intent.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    intent.getIntExtra("subscription",
                    intent.getIntExtra("sub_id", -1)));

            for (SmsMessage msg : messages) {
                if (msg == null) continue;
                String part = msg.getMessageBody();
                if (part != null) {
                    fullMessage.append(part);
                }
            }

            String body = fullMessage.toString();
            // Time of receipt. The PDU timestamp above is the network (service centre) time: it can be wrong,
            // delayed or 0, and storing it as the message date put new messages out of order or at 1970.
            final long receivedAt = System.currentTimeMillis();
            android.util.Log.d("SMS_DEBUG", "SmsReceiver: Incoming SMS action=" + action + ", from=" + SmsHelper.maskAddress(sender)
                    + ", subId=" + subId + ", parts=" + messages.length + ", bodyLen=" + body.length());

            // Do not store, notify or show a popup for an SMS with no text or no sender: it is not a readable
            // message, and storing it is what produced empty bubbles and blank conversations.
            if (sender == null || sender.trim().isEmpty() || !SmsHelper.isValidMessageBody(body)) {
                return;
            }

            final String finalSender = sender;
            final PendingResult pendingResult = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(context);

                    // Check if sender is blocked
                    if (db.blockedNumberDao().isBlockedSync(finalSender)) {
                        // Ignored / filtered
                        android.util.Log.i("SMS_DEBUG", "SmsReceiver: Sender is blocked, dropping message");
                        return;
                    }

                    // Resolved once and reused for the insert and the notification (it used to be looked up twice).
                    // -1 on failure (getOrCreateThreadId returned a made-up id that matched no conversation, so the
                    // message was stored but never shown); the insert then lets the provider assign the thread.
                    long threadId = SmsHelper.getOrCreateThreadIdOrInvalid(context, finalSender);

                    // A conversation in the recycle bin is only hidden; without this the new message would be stored
                    // but never shown in the list. Done before the insert so the list reload it triggers sees it.
                    SmsHelper.restoreThreadFromRecycleBin(context, threadId);

                    // If default SMS app, insert incoming message into device SMS database
                    if (SmsHelper.isDefaultSmsApp(context)) {
                        // A redelivered broadcast (e.g. after the phone process restarts mid-delivery) carries the same
                        // message again. Store it, notify and show the popup only once.
                        if (isAlreadyStored(context, finalSender, body, timestamp)) {
                            android.util.Log.w("SMS_DEBUG", "SmsReceiver: Same message is already stored (redelivered), ignoring it");
                            return;
                        }
                        try {
                            ContentValues values = new ContentValues();
                            values.put("address", finalSender);
                            values.put("body", body);
                            values.put("date", receivedAt);
                            if (timestamp > 0) {
                                values.put("date_sent", timestamp);
                            }
                            values.put("read", 0);
                            values.put("seen", 0);
                            values.put("type", Telephony.Sms.MESSAGE_TYPE_INBOX);
                            if (threadId > 0) {
                                values.put("thread_id", threadId);
                            }
                            if (subId >= 0) {
                                values.put(Telephony.Sms.SUBSCRIPTION_ID, subId);
                            }

                            android.net.Uri inserted = context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
                            if (inserted == null) {
                                android.util.Log.e("SMS_DEBUG", "SmsReceiver: Inbox insert returned null, message was NOT stored");
                            } else {
                                android.util.Log.d("SMS_DEBUG", "SmsReceiver: Inserted SMS into Inbox, uri=" + inserted + ", threadId=" + threadId);
                            }
                            if (inserted != null && threadId <= 0) {
                                try (android.database.Cursor c = context.getContentResolver().query(inserted, new String[]{"thread_id"}, null, null, null)) {
                                    if (c != null && c.moveToFirst()) {
                                        threadId = c.getLong(0);
                                    }
                                } catch (Exception ignored) {}
                            }
                        } catch (Exception e) {
                            android.util.Log.e("SMS_DEBUG", "SmsReceiver: Failed to insert SMS into Inbox", e);
                        }
                    } else {
                        // Only the default SMS app may write incoming SMS. The default app stores this one; this
                        // app shows its notification and picks the message up from the SMS database.
                        android.util.Log.i("SMS_DEBUG", "SmsReceiver: Not the default SMS app, message is stored by the default SMS app (notification only)");
                    }

                    // Check for OTP using intelligent detection
                    String otp = OtpHelper.extractOtp(body);

                    // Show Notification
                    try {
                        NotificationHelper.showIncomingSmsNotification(context, finalSender, body, threadId, otp);
                    } catch (Exception e) {
                        android.util.Log.e("SMS_DEBUG", "SmsReceiver: Failed to show notification", e);
                    }

                    // Check Instant Message Reply popup setting
                    if (PreferenceManager.getInstance(context).isInstantReplyEnabled()) {
                        Intent popupIntent = new Intent(context, PopupMessageActivity.class);
                        popupIntent.putExtra("address", finalSender);
                        popupIntent.putExtra("message", body);
                        popupIntent.putExtra("thread_id", threadId);
                        popupIntent.putExtra("otp", otp);
                        popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(popupIntent);
                    }
                } catch (Exception e) {
                    android.util.Log.e("SMS_DEBUG", "SmsReceiver: Uncaught error processing SMS", e);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }
}
