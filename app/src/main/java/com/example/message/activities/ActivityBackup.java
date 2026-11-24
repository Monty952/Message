package com.example.message.activities;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;

import com.example.message.R;
import com.example.message.models.BackupData;
import com.example.message.models.Conversation;
import com.example.message.models.MessageItem;
import com.example.message.preferences.PreferenceManager;
import com.example.message.utils.SmsHelper;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityBackup extends BaseActivity {

    private Toolbar toolbar;
    private TextView tvLastBackup;
    private Button btnBackup, btnRestore;

    private PreferenceManager prefManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault());

    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        prefManager = new PreferenceManager(this);

        initViews();
        setupToolbar();
        setupLaunchers();
        updateLastBackupText();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvLastBackup = findViewById(R.id.tvLastBackup);
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void updateLastBackupText() {
        long last = prefManager.getLastBackupTime();
        if (last > 0) {
            tvLastBackup.setText(getString(R.string.last_backup_format, dateFormat.format(new Date(last))));
        } else {
            tvLastBackup.setText(R.string.no_backup_found);
        }
    }

    private void setupLaunchers() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        performBackupToUri(uri);
                    }
                }
        );

        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        performRestoreFromUri(uri);
                    }
                }
        );
    }

    private void setupListeners() {
        btnBackup.setOnClickListener(v -> {
            String filename = "messages_backup_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".json";
            createDocumentLauncher.launch(filename);
        });

        btnRestore.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"application/json", "*/*"}));
    }

    private void performBackupToUri(Uri uri) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(getString(R.string.backing_up_messages));
        progress.setCancelable(false);
        progress.show();

        executor.execute(() -> {
            try {
                List<Conversation> convs = SmsHelper.getConversations(this);
                List<MessageItem> allMessages = new ArrayList<>();
                for (Conversation c : convs) {
                    allMessages.addAll(SmsHelper.getMessages(this, c.getThreadId()));
                }

                BackupData data = new BackupData(System.currentTimeMillis(), "1.0.0", allMessages.size(), allMessages);
                String json = new Gson().toJson(data);

                try (OutputStream os = getContentResolver().openOutputStream(uri);
                     OutputStreamWriter writer = new OutputStreamWriter(os)) {
                    writer.write(json);
                    writer.flush();
                }

                prefManager.setLastBackupTime(System.currentTimeMillis());

                mainHandler.post(() -> {
                    progress.dismiss();
                    updateLastBackupText();
                    Toast.makeText(this, getString(R.string.backup_completed_success, allMessages.size()), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(this, getString(R.string.failed_to_create_backup, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void performRestoreFromUri(Uri uri) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(getString(R.string.restoring_messages));
        progress.setCancelable(false);
        progress.show();

        executor.execute(() -> {
            try {
                BackupData data;
                try (InputStream is = getContentResolver().openInputStream(uri);
                     InputStreamReader reader = new InputStreamReader(is)) {
                    data = new Gson().fromJson(reader, BackupData.class);
                }

                if (data != null && data.getMessages() != null) {
                    int restored = 0;
                    for (MessageItem msg : data.getMessages()) {
                        // insertMessage() rejects entries without text (e.g. empty drafts saved in older backups),
                        // so they are no longer restored as empty "sent" messages.
                        if (SmsHelper.insertMessage(this, msg)) {
                            restored++;
                        }
                    }
                    int count = restored;
                    mainHandler.post(() -> {
                        progress.dismiss();
                        Toast.makeText(this, getString(R.string.restored_messages_success, count), Toast.LENGTH_LONG).show();
                    });
                } else {
                    mainHandler.post(() -> {
                        progress.dismiss();
                        Toast.makeText(this, R.string.invalid_backup_file, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(this, getString(R.string.restore_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
