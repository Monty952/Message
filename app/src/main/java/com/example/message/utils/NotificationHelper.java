package com.example.message.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.example.message.R;
import com.example.message.activities.ActivityChat;
import com.example.message.preferences.PreferenceManager;
import com.example.message.receivers.NotificationActionReceiver;
import com.example.message.receivers.SmsReceiver;

public class NotificationHelper {
    public static final String CHANNEL_MESSAGES = "channel_messages_sms";
    public static final String CHANNEL_AFTER_CALL = "channel_after_call_sms";
    public static final String CHANNEL_SCHEDULE = "channel_schedule_sms";

    public static final String KEY_TEXT_REPLY = "key_text_reply";
    public static final String ACTION_COPY_OTP = "com.example.message.ACTION_COPY_OTP";
    public static final String ACTION_MARK_AS_READ = "com.example.message.ACTION_MARK_AS_READ";
    public static final String EXTRA_OTP_CODE = "extra_otp_code";
    public static final String EXTRA_THREAD_ID = "extra_thread_id";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel msgChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    context.getString(R.string.app_name) + " Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            msgChannel.enableVibration(true);
            msgChannel.setShowBadge(true);
            msgChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                msgChannel.setAllowBubbles(true);
            }
            nm.createNotificationChannel(msgChannel);

            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_AFTER_CALL,
                    context.getString(R.string.call_summary),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(callChannel);

            NotificationChannel schedChannel = new NotificationChannel(
                    CHANNEL_SCHEDULE,
                    context.getString(R.string.scheduled_messages),
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(schedChannel);
        }
    }

    /** Id of a conversation's incoming-message notification. Also used to cancel it after a quick reply. */
    public static int getNotificationId(long threadId, String sender) {
        return (threadId > 0) ? (int) threadId : Math.abs(sender != null ? sender.hashCode() : 0);
    }

    public static void showIncomingSmsNotification(Context context, String sender, String message, long threadId, String otpCode) {
        createNotificationChannels(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        // Auto-extract OTP if not supplied directly
        if (otpCode == null || otpCode.trim().isEmpty()) {
            otpCode = OtpHelper.extractOtp(message);
        }

        String contactName = ContactHelper.getContactName(context, sender);
        int notifId = getNotificationId(threadId, sender);

        // Open chat intent
        Intent chatIntent = new Intent(context, ActivityChat.class);
        chatIntent.putExtra("thread_id", threadId);
        chatIntent.putExtra("address", sender);
        chatIntent.putExtra("contact_name", contactName);
        chatIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_seen)
                .setContentTitle(contactName)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        // Check chat bubbles preference and OS support (Android 11 / API 30+)
        boolean bubblesEnabled = PreferenceManager.getInstance(context).isChatBubblesEnabled();
        boolean supportsBubbles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

        IconCompat senderIcon = IconCompat.createWithResource(context, R.drawable.c_msg);
        Person senderPerson = new Person.Builder()
                .setName(contactName)
                .setIcon(senderIcon)
                .setKey(sender)
                .build();
        Person userPerson = new Person.Builder()
                .setName("Me")
                .build();

        if (bubblesEnabled && supportsBubbles) {
            try {
                String shortcutId = "bubble_conv_" + notifId;

                // Push dynamic shortcut for conversation
                Intent shortcutIntent = new Intent(context, ActivityChat.class);
                shortcutIntent.setAction(Intent.ACTION_VIEW);
                shortcutIntent.putExtra("thread_id", threadId);
                shortcutIntent.putExtra("address", sender);
                shortcutIntent.putExtra("contact_name", contactName);

                ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, shortcutId)
                        .setLongLived(true)
                        .setPerson(senderPerson)
                        .setShortLabel(contactName)
                        .setIcon(senderIcon)
                        .setIntent(shortcutIntent)
                        .build();
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);

                // Bubble PendingIntent (MUST be mutable on API 31+)
                int bubblePendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bubblePendingFlags |= PendingIntent.FLAG_MUTABLE;
                }

                Intent bubbleIntent = new Intent(context, ActivityChat.class);
                bubbleIntent.setAction(Intent.ACTION_VIEW);
                bubbleIntent.putExtra("thread_id", threadId);
                bubbleIntent.putExtra("address", sender);
                bubbleIntent.putExtra("contact_name", contactName);

                PendingIntent bubblePendingIntent = PendingIntent.getActivity(
                        context,
                        notifId,
                        bubbleIntent,
                        bubblePendingFlags
                );

                NotificationCompat.BubbleMetadata bubbleMetadata = new NotificationCompat.BubbleMetadata.Builder(
                        bubblePendingIntent,
                        senderIcon
                )
                        .setDesiredHeight(600)
                        .setAutoExpandBubble(false)
                        .setSuppressNotification(false)
                        .build();

                builder.setBubbleMetadata(bubbleMetadata);
                builder.setShortcutId(shortcutId);
                builder.addPerson(senderPerson);

                // Conversation MessagingStyle
                NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(userPerson)
                        .setConversationTitle(contactName)
                        .addMessage(message, System.currentTimeMillis(), senderPerson);
                builder.setStyle(messagingStyle);
            } catch (Throwable t) {
                // If bubble or shortcut creation fails due to platform or launcher restrictions, fallback safely to BigTextStyle
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            }
        } else {
            // Standard notification style fallback
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
        }

        // Display OTP prominently if present
        if (otpCode != null && !otpCode.isEmpty()) {
            builder.setSubText(context.getString(R.string.otp_pill_text, otpCode));
            if (!bubblesEnabled || !supportsBubbles) {
                String bigContent = context.getString(R.string.otp_pill_text, otpCode) + "\n\n" + message;
                builder.setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(contactName + " (" + context.getString(R.string.otp_pill_text, otpCode) + ")")
                        .bigText(bigContent));
            }

            // Copy OTP Action (placed as first prominent action for OTP messages)
            Intent copyIntent = new Intent(context, NotificationActionReceiver.class);
            copyIntent.setAction(ACTION_COPY_OTP);
            copyIntent.putExtra(EXTRA_OTP_CODE, otpCode);
            copyIntent.putExtra(EXTRA_THREAD_ID, threadId);

            int copyReqCode = notifId + 2000;
            PendingIntent copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    copyReqCode,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Action copyAction = new NotificationCompat.Action.Builder(
                    R.drawable.ic_forword,
                    context.getString(R.string.copy_otp_format, otpCode),
                    copyPendingIntent
            ).build();

            builder.addAction(copyAction);
        }

        // Quick Reply Action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel(context.getString(R.string.reply))
                    .build();

            Intent replyIntent = new Intent(context, NotificationActionReceiver.class);
            replyIntent.setAction("com.example.message.ACTION_REPLY");
            replyIntent.putExtra("address", sender);
            replyIntent.putExtra("thread_id", threadId);

            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notifId + 1000,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                    R.drawable.c_msg,
                    context.getString(R.string.reply),
                    replyPendingIntent
            ).addRemoteInput(remoteInput).build();

            builder.addAction(replyAction);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notifId, builder.build());
        }
    }

    public static void showIncomingMmsNotification(Context context, String sender, String message, long threadId, String attachmentUri, String mimeType) {
        showIncomingSmsNotification(context, sender, message, threadId, null);
    }
}
