package com.example.message.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.message.R;
import com.example.message.utils.ContactHelper;

public class PopupMessageActivity extends BaseActivity {

    private TextView tvAvatar, tvSender, tvNumber, tvBody;
    private Button btnDismiss, btnReply;

    private String address = "";
    private String body = "";
    private String senderName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_popup_message);

        parseIntent();
        initViews();
        setupData();
        setupListeners();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            address = intent.getStringExtra("address");
            if (address == null) address = "";
            // SmsReceiver passes the text as the "message" extra. Reading only "body" left every popup showing an
            // empty message. "body" is still read as a fallback for any other caller.
            body = intent.getStringExtra("message");
            if (body == null) body = intent.getStringExtra("body");
            if (body == null) body = "";
            senderName = intent.getStringExtra("sender_name");
            if (TextUtils.isEmpty(senderName)) {
                senderName = ContactHelper.getContactName(this, address);
            }
        }
    }

    private void initViews() {
        tvAvatar = findViewById(R.id.tvAvatar);
        tvSender = findViewById(R.id.tvSender);
        tvNumber = findViewById(R.id.tvNumber);
        tvBody = findViewById(R.id.tvBody);
        btnDismiss = findViewById(R.id.btnDismiss);
        btnReply = findViewById(R.id.btnReply);
    }

    private void setupData() {
        String displayName = !TextUtils.isEmpty(senderName) ? senderName : address;
        tvSender.setText(displayName);
        tvNumber.setText(address);
        tvBody.setText(body);

        String initial = !TextUtils.isEmpty(displayName) ? String.valueOf(displayName.charAt(0)).toUpperCase() : "?";
        tvAvatar.setText(initial);
    }

    private void setupListeners() {
        btnDismiss.setOnClickListener(v -> finish());

        btnReply.setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ActivityChat.class);
            chatIntent.putExtra("address", address);
            chatIntent.putExtra("contact_name", senderName);
            chatIntent.putExtra("thread_id", -1L);
            startActivity(chatIntent);
            finish();
        });
    }
}
