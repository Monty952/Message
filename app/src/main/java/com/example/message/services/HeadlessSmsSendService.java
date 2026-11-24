package com.example.message.services;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.message.utils.SmsHelper;

public class HeadlessSmsSendService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)
                    || "android.intent.action.RESPOND_VIA_MESSAGE".equals(action)) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    String message = extras.getString(Intent.EXTRA_TEXT);
                    Uri data = intent.getData();
                    String recipient = null;
                    if (data != null) {
                        recipient = data.getSchemeSpecificPart();
                    }
                    if (!TextUtils.isEmpty(recipient) && !TextUtils.isEmpty(message)) {
                        SmsHelper.sendSms(this, recipient, message);
                    }
                }
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
