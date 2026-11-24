package com.example.message.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.message.R;
import com.example.message.database.AppDatabase;
import com.example.message.database.BlockedNumberEntity;
import com.example.message.utils.ContactHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AfterCallDetailActivity extends BaseActivity {

    private TextView tvAvatar, tvContactName, tvPhoneNumber, tvDismiss;
    private LinearLayout llCall, llMessage, llSave, llBlock;

    private String number = "";
    private String contactName = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_after_call_details);

        number = getIntent().getStringExtra("number");
        if (number == null) number = "";

        contactName = ContactHelper.getContactName(this, number);

        initViews();
        setupInfo();
        setupListeners();
    }

    private void initViews() {
        tvAvatar = findViewById(R.id.tvAvatar);
        tvContactName = findViewById(R.id.tvContactName);
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber);
        tvDismiss = findViewById(R.id.tvDismiss);
        llCall = findViewById(R.id.llCall);
        llMessage = findViewById(R.id.llMessage);
        llSave = findViewById(R.id.llSave);
        llBlock = findViewById(R.id.llBlock);
    }

    private void setupInfo() {
        String displayName = !TextUtils.isEmpty(contactName) ? contactName : number;
        tvContactName.setText(displayName);
        tvPhoneNumber.setText(number);

        String initial = !TextUtils.isEmpty(displayName) ? String.valueOf(displayName.charAt(0)).toUpperCase() : "?";
        tvAvatar.setText(initial);
    }

    private void setupListeners() {
        tvDismiss.setOnClickListener(v -> finish());

        llCall.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(number)) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
                startActivity(dialIntent);
            }
            finish();
        });

        llMessage.setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ActivityChat.class);
            chatIntent.putExtra("address", number);
            chatIntent.putExtra("contact_name", contactName);
            chatIntent.putExtra("thread_id", -1L);
            startActivity(chatIntent);
            finish();
        });

        llSave.setOnClickListener(v -> {
            Intent insertContactIntent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
            insertContactIntent.putExtra(ContactsContract.Intents.Insert.PHONE, number);
            startActivity(insertContactIntent);
            finish();
        });

        llBlock.setOnClickListener(v -> {
            executor.execute(() -> {
                BlockedNumberEntity entity = new BlockedNumberEntity(number, contactName, System.currentTimeMillis());
                AppDatabase.getInstance(this).blockedNumberDao().insert(entity);
            });
            Toast.makeText(this, R.string.number_blocked, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
