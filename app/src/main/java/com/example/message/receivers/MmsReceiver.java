package com.example.message.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.util.Log;

import com.example.message.utils.MmsHelper;
import com.example.message.utils.SmsHelper;

public class MmsReceiver extends BroadcastReceiver {
    private static final String TAG = "MmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(action)
                || Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(action)) {

            // Prevent duplicate processing when this app is default SMS client
            if (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(action) && SmsHelper.isDefaultSmsApp(context)) {
                return;
            }

            String mimeType = intent.getType();
            Log.d("MMS_DEBUG", "MmsReceiver: Incoming MMS WAP push intent received. Action=" + action + ", MIME=" + mimeType);
            if ("application/vnd.wap.mms-message".equalsIgnoreCase(mimeType)) {
                Log.i("MMS_DEBUG", "MmsReceiver: Valid MMS WAP push notification received, forwarding to MmsHelper.handleWapPush");
                final PendingResult pendingResult = goAsync();
                new Thread(() -> {
                    try {
                        MmsHelper.handleWapPush(context, intent);
                    } catch (Exception e) {
                        Log.e("MMS_DEBUG", "MmsReceiver: Exception in handleWapPush", e);
                    } finally {
                        pendingResult.finish();
                    }
                }).start();
            }
        }
    }
}
