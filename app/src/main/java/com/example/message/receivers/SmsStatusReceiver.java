package com.example.message.receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import com.example.message.utils.SmsHelper;

import java.util.concurrent.Executors;

public class SmsStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_SMS_SENT = "com.example.message.SMS_SENT";
    public static final String ACTION_SMS_DELIVERED = "com.example.message.SMS_DELIVERED";
    private static final String TAG = "SmsStatusReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        final int resultCode = getResultCode();
        final Uri messageUri = intent.getData() != null ? intent.getData() : (Uri) intent.getParcelableExtra("message_uri");
        final int networkErrorCode = intent.getIntExtra("errorCode", intent.getIntExtra("android.telephony.extra.ERROR_CODE", -1));
        Log.d("SMS_DEBUG", "SmsStatusReceiver: onReceive action=" + action + ", resultCode=" + resultCode + ", networkErrorCode=" + networkErrorCode + ", uri=" + messageUri);

        if (ACTION_SMS_SENT.equals(action)) {
            if (messageUri == null) {
                Log.w("SMS_DEBUG", "SmsStatusReceiver: Sent result has no message uri (message was not stored), status cannot be saved");
                return;
            }
            final PendingResult pendingResult = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ContentValues cv = new ContentValues();
                    int rows;
                    if (resultCode == Activity.RESULT_OK) {
                        Log.d("SMS_DEBUG", "SmsStatusReceiver: SMS sent successfully, updating to SENT");
                        cv.put("type", Telephony.Sms.MESSAGE_TYPE_SENT);
                        // A long SMS is sent in parts and every part reports its own result to this receiver. A later
                        // part's success used to overwrite the failure of an earlier part, so an incomplete message
                        // showed as sent. A row already marked failed stays failed.
                        rows = context.getContentResolver().update(messageUri, cv, "type != ?",
                                new String[]{String.valueOf(Telephony.Sms.MESSAGE_TYPE_FAILED)});
                    } else {
                        String failureReason;
                        switch (resultCode) {
                            case 1: failureReason = "GENERIC_FAILURE (1)"; break;
                            case 2: failureReason = "RADIO_OFF (2)"; break;
                            case 3: failureReason = "NULL_PDU (3)"; break;
                            case 4: failureReason = "NO_SERVICE (4)"; break;
                            case 5: failureReason = "LIMIT_EXCEEDED (5)"; break;
                            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE: failureReason = "FDN_CHECK_FAILURE (" + resultCode + ") - number not allowed by Fixed Dialing Numbers"; break;
                            case SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED: failureReason = "SHORT_CODE_NOT_ALLOWED (" + resultCode + ") - user did not allow sending to this short code"; break;
                            case SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED: failureReason = "SHORT_CODE_NEVER_ALLOWED (" + resultCode + ") - sending to this short code is blocked"; break;
                            case SmsManager.RESULT_RADIO_NOT_AVAILABLE: failureReason = "RADIO_NOT_AVAILABLE (" + resultCode + ")"; break;
                            case SmsManager.RESULT_NETWORK_REJECT: failureReason = "NETWORK_REJECT (" + resultCode + ")"; break;
                            case 17: failureReason = "RESULT_NETWORK_ERROR (17) - Carrier/Network rejected SMS (no SMS balance/pack, inactive SIM, or wrong SIM selected)"; break;
                            case SmsManager.RESULT_SMS_SEND_RETRY_FAILED: failureReason = "SMS_SEND_RETRY_FAILED (" + resultCode + ") - all retries failed"; break;
                            case SmsManager.RESULT_NO_DEFAULT_SMS_APP: failureReason = "NO_DEFAULT_SMS_APP (" + resultCode + ") - no SIM could be chosen (no default SMS SIM and the SIM picker was not shown or was dismissed)"; break;
                            default: failureReason = "Error code " + resultCode; break;
                        }
                        Log.e("SMS_DEBUG", "SmsStatusReceiver: SMS sending failed with " + failureReason + ", networkErrorCode=" + networkErrorCode);
                        cv.put("type", Telephony.Sms.MESSAGE_TYPE_FAILED);
                        cv.put("status", Telephony.Sms.STATUS_FAILED);
                        rows = context.getContentResolver().update(messageUri, cv, null, null);
                    }
                    if (rows == 0) {
                        // Expected when an earlier part already failed. Otherwise: the row was deleted, or this app is
                        // not the default SMS app (Android ignores its writes and keeps the status itself).
                        Log.w("SMS_DEBUG", "SmsStatusReceiver: Status not written (0 rows) for uri=" + messageUri
                                + ", resultCode=" + resultCode + ", isDefaultSmsApp=" + SmsHelper.isDefaultSmsApp(context));
                    }
                } catch (Exception e) {
                    Log.e("SMS_DEBUG", "Failed to update SMS sent status", e);
                } finally {
                    pendingResult.finish();
                }
            });

        } else if (ACTION_SMS_DELIVERED.equals(action)) {
            if (messageUri == null) return;
            final PendingResult pendingResult = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put("status", Telephony.Sms.STATUS_COMPLETE);
                    context.getContentResolver().update(messageUri, cv, null, null);
                    Log.d("SMS_DEBUG", "SmsStatusReceiver: SMS marked delivered");
                } catch (Exception e) {
                    Log.e("SMS_DEBUG", "Failed to update SMS delivery status", e);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }
}
