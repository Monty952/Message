package com.example.message.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.message.utils.NotificationHelper;
import com.example.message.utils.SmsHelper;

/**
 * Handles notification actions (such as Copy OTP and Quick Reply) without
 * requiring TELEPHONY BROADCAST_SMS permissions that would block SystemUI.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (NotificationHelper.ACTION_COPY_OTP.equals(action)) {
            SmsReceiver.handleCopyOtp(context, intent);
        } else if ("com.example.message.ACTION_REPLY".equals(action)) {
            final PendingResult pendingResult = goAsync();
            androidx.core.app.RemoteInput remoteInput = null;
            android.os.Bundle results = androidx.core.app.RemoteInput.getResultsFromIntent(intent);
            if (results != null) {
                CharSequence replyText = results.getCharSequence(NotificationHelper.KEY_TEXT_REPLY);
                String address = intent.getStringExtra("address");
                final long threadId = intent.getLongExtra("thread_id", -1);
                if (replyText != null && address != null) {
                    java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            // Reply on the conversation's SIM, as the chat screen does (it used to use the default SIM).
                            int threadSubId = SmsHelper.getThreadSubscriptionId(context, threadId);
                            boolean queued = threadSubId >= 0
                                    ? SmsHelper.sendSms(context, address, replyText.toString(), threadSubId)
                                    : SmsHelper.sendSms(context, address, replyText.toString());
                            Log.d("SMS_DEBUG", "NotificationActionReceiver: Quick reply queued=" + queued + ", threadId=" + threadId);
                        } catch (Exception e) {
                            Log.e("SMS_DEBUG", "NotificationActionReceiver: Quick reply failed", e);
                        } finally {
                            // Android keeps a replied notification in its "sending" state until the app updates or
                            // cancels it, so the reply looked stuck. The sent message itself shows in the chat.
                            try {
                                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                                if (nm != null) {
                                    nm.cancel(NotificationHelper.getNotificationId(threadId, address));
                                }
                            } catch (Exception e) {
                                Log.w("SMS_DEBUG", "NotificationActionReceiver: Could not clear the notification", e);
                            }
                            pendingResult.finish();
                        }
                    });
                    return;
                }
            }
            Log.w("SMS_DEBUG", "NotificationActionReceiver: Quick reply without text or address, ignored");
            pendingResult.finish();
        }
    }
}
