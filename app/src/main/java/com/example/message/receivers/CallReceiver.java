package com.example.message.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import com.example.message.activities.AfterCallDetailActivity;
import com.example.message.preferences.PreferenceManager;

public class CallReceiver extends BroadcastReceiver {
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private static String savedNumber = null;
    private static long callStartTime = 0;
    private static boolean isIncoming = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PreferenceManager.getInstance(context).isAfterCallEnabled()) {
            return;
        }

        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            return;
        }

        if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (number != null && !number.isEmpty()) {
                savedNumber = number;
            }

            int state = TelephonyManager.CALL_STATE_IDLE;
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(stateStr)) {
                state = TelephonyManager.CALL_STATE_RINGING;
            } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr)) {
                state = TelephonyManager.CALL_STATE_OFFHOOK;
            }

            onCustomCallStateChanged(context, state, savedNumber);
        }
    }

    private void onCustomCallStateChanged(Context context, int state, String number) {
        if (lastState == state) {
            return;
        }

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                isIncoming = true;
                callStartTime = System.currentTimeMillis();
                savedNumber = number;
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false;
                    callStartTime = System.currentTimeMillis();
                }
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                // Call ended
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK || lastState == TelephonyManager.CALL_STATE_RINGING) {
                    long durationSec = (System.currentTimeMillis() - callStartTime) / 1000;
                    launchAfterCallActivity(context, number, durationSec, isIncoming);
                }
                break;
        }
        lastState = state;
    }

    private void launchAfterCallActivity(Context context, String number, long durationSec, boolean incoming) {
        if (number == null || number.trim().isEmpty()) {
            number = "Unknown";
        }
        Intent intent = new Intent(context, AfterCallDetailActivity.class);
        intent.putExtra("number", number);
        intent.putExtra("duration_sec", durationSec);
        intent.putExtra("is_incoming", incoming);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
