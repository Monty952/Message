package com.example.message.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.example.message.R;
import com.example.message.database.AppDatabase;
import com.example.message.database.ArchiveEntity;
import com.example.message.database.BlockedNumberEntity;
import com.example.message.database.RecycleBinEntity;
import com.example.message.utils.ContactHelper;
import com.example.message.utils.SmsHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityInfo extends BaseActivity {

    private Toolbar toolbar;
    private TextView tvInitials, tvName, tvAddress, tvPhoneNumber, tvThreadId;
    private LinearLayout llCall, llMessage, llBlock;
    private ImageView ivBlockIcon;
    private TextView tvBlockLabel;
    private Button btnArchive, btnDelete;

    private long threadId = -1;
    private String address = "";
    private String contactName = "";
    private boolean isBlocked = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        parseIntent();
        initViews();
        setupToolbar();
        setupInfo();
        setupActions();
        checkBlockStatus();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            threadId = intent.getLongExtra("thread_id", -1);
            address = intent.getStringExtra("address");
            if (address == null) address = "";
            contactName = intent.getStringExtra("contact_name");
            if (TextUtils.isEmpty(contactName)) {
                contactName = ContactHelper.getContactName(this, address);
            }
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvInitials = findViewById(R.id.tvInitials);
        tvName = findViewById(R.id.tvName);
        tvAddress = findViewById(R.id.tvAddress);
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber);
        tvThreadId = findViewById(R.id.tvThreadId);
        llCall = findViewById(R.id.llCall);
        llMessage = findViewById(R.id.llMessage);
        llBlock = findViewById(R.id.llBlock);
        ivBlockIcon = findViewById(R.id.ivBlockIcon);
        tvBlockLabel = findViewById(R.id.tvBlockLabel);
        btnArchive = findViewById(R.id.btnArchive);
        btnDelete = findViewById(R.id.btnDelete);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupInfo() {
        String displayName = !TextUtils.isEmpty(contactName) ? contactName : address;
        tvName.setText(displayName);
        tvAddress.setText(address);
        tvPhoneNumber.setText(address);
        tvThreadId.setText(threadId > 0 ? String.valueOf(threadId) : getString(R.string.not_available));

        String initial = !TextUtils.isEmpty(displayName) ? String.valueOf(displayName.charAt(0)).toUpperCase() : "?";
        tvInitials.setText(initial);
    }

    private void setupActions() {
        llCall.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(address)) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)));
                startActivity(dialIntent);
            }
        });

        llMessage.setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ActivityChat.class);
            chatIntent.putExtra("thread_id", threadId);
            chatIntent.putExtra("address", address);
            chatIntent.putExtra("contact_name", contactName);
            startActivity(chatIntent);
            finish();
        });

        llBlock.setOnClickListener(v -> toggleBlock());

        btnArchive.setOnClickListener(v -> archiveConversation());

        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void checkBlockStatus() {
        executor.execute(() -> {
            isBlocked = AppDatabase.getInstance(this).blockedNumberDao().isBlockedSync(address);
            mainHandler.post(() -> {
                if (isBlocked) {
                    ivBlockIcon.setImageResource(R.drawable.c_unblock);
                    tvBlockLabel.setText(R.string.unblock);
                } else {
                    ivBlockIcon.setImageResource(R.drawable.c_block);
                    tvBlockLabel.setText(R.string.block);
                }
            });
        });
    }

    private void toggleBlock() {
        executor.execute(() -> {
            if (isBlocked) {
                AppDatabase.getInstance(this).blockedNumberDao().deleteByNumber(address);
                isBlocked = false;
                mainHandler.post(() -> {
                    ivBlockIcon.setImageResource(R.drawable.c_block);
                    tvBlockLabel.setText(R.string.block);
                    Toast.makeText(this, R.string.number_unblocked, Toast.LENGTH_SHORT).show();
                });
            } else {
                BlockedNumberEntity entity = new BlockedNumberEntity(address, contactName, System.currentTimeMillis());
                AppDatabase.getInstance(this).blockedNumberDao().insert(entity);
                isBlocked = true;
                mainHandler.post(() -> {
                    ivBlockIcon.setImageResource(R.drawable.c_unblock);
                    tvBlockLabel.setText(R.string.unblock);
                    Toast.makeText(this, R.string.number_blocked, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void archiveConversation() {
        executor.execute(() -> {
            ArchiveEntity entity = new ArchiveEntity(threadId, address, contactName, System.currentTimeMillis());
            AppDatabase.getInstance(this).archiveDao().insert(entity);
            mainHandler.post(() -> {
                Toast.makeText(this, R.string.archived_successfully, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_conversation_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    executor.execute(() -> {
                        RecycleBinEntity entity = new RecycleBinEntity(threadId, address, contactName, System.currentTimeMillis());
                        AppDatabase.getInstance(this).recycleBinDao().insert(entity);
                        mainHandler.post(() -> {
                            Toast.makeText(this, R.string.moved_to_recycle_bin, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
