package com.example.message.receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import com.example.message.utils.MmsHelper;

import java.io.File;
import java.util.concurrent.Executors;

public class MmsStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_MMS_SENT = "com.example.message.MMS_SENT";
    public static final String ACTION_MMS_DOWNLOADED = "com.example.message.MMS_DOWNLOADED";
    private static final String TAG = "MmsStatusReceiver";

    private static String getResultCodeName(int code) {
        switch (code) {
            case Activity.RESULT_OK: return "RESULT_OK (-1)";
            case 1: return "MMS_ERROR_UNSPECIFIED (1)";
            case 2: return "MMS_ERROR_INVALID_APN (2)";
            case 3: return "MMS_ERROR_UNABLE_CONNECT_MMSC (3)";
            case 4: return "MMS_ERROR_HTTP_FAILURE (4)";
            case 5: return "MMS_ERROR_IO_ERROR (5)";
            case 6: return "MMS_ERROR_RETRY (6)";
            case 7: return "MMS_ERROR_CONFIGURATION_ERROR (7)";
            case 8: return "MMS_ERROR_NO_DATA (8)";
            default: return "CODE_" + code;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        final int resultCode = getResultCode();

        if (ACTION_MMS_SENT.equals(action)) {
            final long mmsId = intent.getLongExtra("mms_id", -1);
            final String filePath = intent.getStringExtra("file_path");

            Log.d("MMS_DEBUG", "MmsStatusReceiver: MMS sent callback received for id=" + mmsId
                    + ", resultCode=" + getResultCodeName(resultCode));

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        Log.i("MMS_DEBUG", "MmsStatusReceiver: MMS transmission succeeded for id: " + mmsId);
                        if (mmsId > 0) {
                            ContentValues cv = new ContentValues();
                            cv.put("msg_box", Telephony.Mms.MESSAGE_BOX_SENT);
                            context.getContentResolver().update(
                                    ContentUris.withAppendedId(MmsHelper.MMS_URI, mmsId),
                                    cv, null, null
                            );
                        }
                    } else {
                        Log.w("MMS_DEBUG", "MmsStatusReceiver: MMS transmission failed with code: " + getResultCodeName(resultCode));
                    }
                    if (filePath != null) {
                        new File(filePath).delete();
                    }
                } catch (Exception e) {
                    Log.e("MMS_DEBUG", "MmsStatusReceiver: Error updating sent MMS status", e);
                }
            });

        } else if (ACTION_MMS_DOWNLOADED.equals(action)) {
            final long mmsId = intent.getLongExtra("mms_id", -1);
            final long threadId = intent.getLongExtra("thread_id", -1);
            final String sender = intent.getStringExtra("sender");
            final String filePath = intent.getStringExtra("file_path");
            final int httpStatus = intent.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS", 0);

            Log.d("MMS_DEBUG", "MmsStatusReceiver: Download callback received for MMS id=" + mmsId
                    + ", resultCode=" + getResultCodeName(resultCode) + " (" + resultCode + ")"
                    + ", httpStatus=" + httpStatus);

            Executors.newSingleThreadExecutor().execute(() -> {
                if (filePath == null) {
                    Log.e("MMS_DEBUG", "MmsStatusReceiver: Download callback filePath is null!");
                    return;
                }
                File file = new File(filePath);
                boolean exists = file.exists();
                long length = exists ? file.length() : 0;
                Log.d("MMS_DEBUG", "MmsStatusReceiver: Downloaded file path=" + filePath
                        + ", exists=" + exists + ", size=" + length + " bytes");

                if (resultCode == Activity.RESULT_OK && exists && length > 0) {
                    Log.i("MMS_DEBUG", "MmsStatusReceiver: MMS download callback succeeded! Initiating processDownloadedMms for id=" + mmsId);
                    MmsHelper.processDownloadedMms(context, mmsId, threadId, sender, filePath);
                } else {
                    Log.w("MMS_DEBUG", "MmsStatusReceiver: MMS download failed! ResultCode="
                            + getResultCodeName(resultCode) + ", httpStatus=" + httpStatus
                            + ", file exists=" + exists + ", file size=" + length);
                }
            });
        }
    }
}
