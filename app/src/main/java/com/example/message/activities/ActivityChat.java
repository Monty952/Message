package com.example.message.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.message.R;
import com.example.message.adapters.MessageAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.BlockedNumberEntity;
import com.example.message.database.ScheduledMessageEntity;
import com.example.message.models.MessageItem;
import com.example.message.utils.ContactHelper;
import com.example.message.utils.MmsHelper;
import com.example.message.utils.SmsHelper;
import com.example.message.workers.ScheduledSmsWorker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActivityChat extends BaseActivity {

    private ImageView ivBack, ivCall, ivInfo;
    private FrameLayout flAvatar;
    private ImageView ivAvatar;
    private TextView tvAvatarInitial;
    private LinearLayout llContactInfo;
    private TextView tvContactName, tvAddress;
    private LinearLayout blockedBanner;
    private Button btnUnblock;
    private RecyclerView rvMessages;
    private LinearLayout inputBar;
    private ImageView ivSchedule, ivEmoji, ivSend;
    private EditText etMessage;
    private ProgressBar progressLoading;

    // Written on the background executor and read on the main thread.
    private volatile long threadId = -1;
    private String address = "";
    private String contactName = "";

    private ActivityResultLauncher<String> mediaPickerLauncher;
    private ActivityResultLauncher<String> sendSmsPermissionLauncher;
    private Uri pendingAttachmentUri = null;
    private String pendingAttachmentType = null;

    private MessageAdapter messageAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver smsObserver;

    /** Wait before showing the loader, so it never flashes when loading is quick. */
    private static final long LOADER_DELAY_MS = 300;
    /** Batches the bursts of change notifications that SMS database writes produce into one reload. */
    private static final long OBSERVER_DEBOUNCE_MS = 300;

    /** True while a load is queued but not yet started. Extra requests in that window are merged into it. */
    private final AtomicBoolean loadQueued = new AtomicBoolean(false);
    /** Main thread only. */
    private boolean initialLoadDone = false;
    private final Runnable showLoaderRunnable = this::showLoaderIfNothingDisplayed;
    private final Runnable observerReloadRunnable = this::loadMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_message);

        setupMediaPicker();
        sendSmsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        sendMessage();
                    } else {
                        Log.w("SMS_DEBUG", "ActivityChat: SEND_SMS permission denied, message not sent");
                        Toast.makeText(this, R.string.failed_to_send_sms, Toast.LENGTH_SHORT).show();
                    }
                }
        );
        parseIntent();
        initViews();
        setupToolbar();
        setupRecyclerView();
        setupInputBar();
        // checkBlockStatus() and loadMessages() are not called here: onResume() always runs right after
        // onCreate() and calls both. Calling them here too loaded every conversation twice on open.
        registerSmsObserver();
    }

    private void setupMediaPicker() {
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pendingAttachmentUri = uri;
                        try {
                            pendingAttachmentType = getContentResolver().getType(uri);
                        } catch (Exception e) {
                            pendingAttachmentType = "image/*";
                        }
                        if (pendingAttachmentType == null) pendingAttachmentType = "image/*";
                        if (ivSend != null) ivSend.setVisibility(View.VISIBLE);
                        if (ivEmoji != null) ivEmoji.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.attach_media, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseIntent();
        setupToolbar();
        loadMessages();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            threadId = intent.getLongExtra("thread_id", -1);
            address = intent.getStringExtra("address");
            if (address == null) address = "";
            contactName = intent.getStringExtra("contact_name");
            if (contactName == null || contactName.isEmpty()) {
                contactName = ContactHelper.getContactName(this, address);
            }

            String forwardBody = intent.getStringExtra("forward_body");
            if (!TextUtils.isEmpty(forwardBody) && etMessage != null) {
                etMessage.setText(forwardBody);
            }
            String smsBody = intent.getStringExtra("sms_body");
            if (!TextUtils.isEmpty(smsBody) && etMessage != null) {
                etMessage.setText(smsBody);
            }
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(extraText) && etMessage != null) {
                etMessage.setText(extraText);
            }
            try {
                Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (streamUri != null) {
                    pendingAttachmentUri = streamUri;
                    pendingAttachmentType = getContentResolver().getType(streamUri);
                    if (pendingAttachmentType == null) pendingAttachmentType = "image/*";
                }
            } catch (Exception ignored) {}
        }
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        ivCall = findViewById(R.id.ivCall);
        ivInfo = findViewById(R.id.ivInfo);
        flAvatar = findViewById(R.id.flAvatar);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        llContactInfo = findViewById(R.id.llContactInfo);
        tvContactName = findViewById(R.id.tvContactName);
        tvAddress = findViewById(R.id.tvAddress);
        blockedBanner = findViewById(R.id.blockedBanner);
        btnUnblock = findViewById(R.id.btnUnblock);
        rvMessages = findViewById(R.id.rvMessages);
        inputBar = findViewById(R.id.inputBar);
        ivSchedule = findViewById(R.id.ivSchedule);
        ivEmoji = findViewById(R.id.ivEmoji);
        ivSend = findViewById(R.id.ivSend);
        etMessage = findViewById(R.id.etMessage);
        progressLoading = findViewById(R.id.progressLoading);
    }

    private void setupToolbar() {
        ivBack.setOnClickListener(v -> finish());

        String displayName = (!TextUtils.isEmpty(contactName)) ? contactName : address;
        tvContactName.setText(displayName);
        tvAddress.setText(address);

        String initial = (!TextUtils.isEmpty(displayName)) ? String.valueOf(displayName.charAt(0)).toUpperCase() : "?";
        tvAvatarInitial.setText(initial);

        ivCall.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(address)) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)));
                startActivity(dialIntent);
            }
        });

        View.OnClickListener openInfo = v -> {
            Intent infoIntent = new Intent(ActivityChat.this, ActivityInfo.class);
            infoIntent.putExtra("thread_id", threadId);
            infoIntent.putExtra("address", address);
            infoIntent.putExtra("contact_name", contactName);
            startActivity(infoIntent);
        };

        ivInfo.setOnClickListener(openInfo);
        llContactInfo.setOnClickListener(openInfo);
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(this, new ArrayList<>());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        messageAdapter.setActionListener(new MessageAdapter.OnMessageActionListener() {
            @Override
            public void onCopy(MessageItem item) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("SMS", item.getBody());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(ActivityChat.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onForward(MessageItem item) {
                Intent forwardIntent = new Intent(ActivityChat.this, ActivitySearchContact.class);
                forwardIntent.putExtra("forward_body", item.getBody());
                startActivity(forwardIntent);
            }

            @Override
            public void onDelete(MessageItem item) {
                executor.execute(() -> {
                    if (item.isMms()) {
                        SmsHelper.deleteMmsMessage(ActivityChat.this, item.getId());
                    } else {
                        SmsHelper.deleteMessage(ActivityChat.this, item.getId());
                    }
                    mainHandler.post(() -> loadMessages());
                });
            }

            @Override
            public void onRetry(MessageItem item) {
                new AlertDialog.Builder(ActivityChat.this)
                        .setTitle(R.string.retry)
                        .setMessage(R.string.failed_to_send_sms)
                        .setPositiveButton(R.string.retry, (dialog, which) -> {
                            executor.execute(() -> {
                                if (item.isMms()) {
                                    if (item.hasAttachment()) {
                                        MmsHelper.sendMms(ActivityChat.this, address, item.getBody(), Uri.parse(item.getAttachmentUri()), item.getAttachmentType());
                                    }
                                } else {
                                    SmsHelper.retrySendSms(ActivityChat.this, item.getId(), address, item.getBody());
                                }
                                mainHandler.post(ActivityChat.this::loadMessages);
                            });
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }

            @Override
            public void onDownloadMms(MessageItem item) {
                Toast.makeText(ActivityChat.this, R.string.downloading_mms, Toast.LENGTH_SHORT).show();
                executor.execute(() -> {
                    String contentLoc = null;
                    Uri mmsUri = ContentUris.withAppendedId(MmsHelper.MMS_URI, item.getId());
                    try (Cursor c = getContentResolver().query(mmsUri, new String[]{"ct_l"}, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            contentLoc = c.getString(0);
                        }
                    } catch (Exception ignored) {}
                    Log.d("MMS_DEBUG", "ActivityChat.onDownloadMms: Manual download triggered for MMS id="
                            + item.getId() + ", ct_l=" + contentLoc);
                    if (contentLoc != null) {
                        MmsHelper.downloadMms(ActivityChat.this, item.getId(), threadId, address, contentLoc, SubscriptionManager.getDefaultSmsSubscriptionId());
                    } else {
                        Log.w("MMS_DEBUG", "ActivityChat.onDownloadMms: ct_l is null in DB for MMS id=" + item.getId());
                    }
                });
            }
        });
    }

    private void setupInputBar() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = (s != null && s.toString().trim().length() > 0) || pendingAttachmentUri != null;
                ivSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                ivEmoji.setVisibility(hasText ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ivEmoji.setOnClickListener(v -> {
            if (mediaPickerLauncher != null) {
                mediaPickerLauncher.launch("*/*");
            }
        });
        ivSend.setOnClickListener(v -> sendMessage());
        ivSchedule.setOnClickListener(v -> showScheduleDatePicker());
    }

    private void sendMessage() {
        String body = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(body) && pendingAttachmentUri == null) return;
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(this, R.string.recipient_address_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        if (pendingAttachmentUri == null && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        etMessage.setText("");
        final Uri attUri = pendingAttachmentUri;
        final String attType = pendingAttachmentType;
        pendingAttachmentUri = null;
        pendingAttachmentType = null;
        ivSend.setVisibility(View.GONE);
        ivEmoji.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            // A conversation in the recycle bin is only hidden: without this, the message sent here was stored in
            // that hidden thread and the conversation did not come back to the list.
            SmsHelper.restoreThreadFromRecycleBin(this, threadId);
            boolean success;
            if (attUri != null) {
                success = MmsHelper.sendMms(this, address, body, attUri, attType);
            } else {
                int threadSubId = SmsHelper.getThreadSubscriptionId(this, threadId);
                Log.d("SMS_DEBUG", "ActivityChat.sendMessage: SIM taken from "
                        + (threadSubId >= 0 ? "this conversation's last message (subId=" + threadSubId + ")" : "the phone's default SMS SIM"));
                success = (threadSubId >= 0)
                        ? SmsHelper.sendSms(this, address, body, threadSubId)
                        : SmsHelper.sendSms(this, address, body);
            }
            if (success && threadId <= 0) {
                // New conversation: the thread now holds the message just sent, so resolving it here can no
                // longer leave an empty thread behind (same lookup the screen used to do on open).
                try {
                    threadId = Telephony.Threads.getOrCreateThreadId(this, address);
                } catch (Exception ignored) {}
            }
            mainHandler.post(() -> {
                if (success) {
                    loadMessages();
                } else {
                    Toast.makeText(this, attUri != null ? R.string.failed_to_send_mms : R.string.failed_to_send_sms, Toast.LENGTH_SHORT).show();
                }
            });
        });
        // Never log the message text itself (it can hold OTPs and personal content).
        Log.d("SMS_DEBUG", "ActivityChat.sendMessage: queued, length=" + body.length()
                + ", attachment=" + (attUri != null) + ", threadId=" + threadId);
    }

    private void showScheduleDatePicker() {
        String body = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(body)) {
            Toast.makeText(this, R.string.please_enter_message_text, Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                long scheduledTime = calendar.getTimeInMillis();
                if (scheduledTime <= System.currentTimeMillis()) {
                    Toast.makeText(this, R.string.scheduled_time_future, Toast.LENGTH_SHORT).show();
                    return;
                }

                scheduleMessage(body, scheduledTime);
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void scheduleMessage(String body, long scheduledTime) {
        executor.execute(() -> {
            ScheduledMessageEntity entity = new ScheduledMessageEntity(address, contactName, body, scheduledTime);
            long id = AppDatabase.getInstance(this).scheduledMessageDao().insert(entity);

            long delay = scheduledTime - System.currentTimeMillis();
            Data inputData = new Data.Builder()
                    .putLong("scheduled_id", id)
                    .putString("address", address)
                    .putString("message", body)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScheduledSmsWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("schedMsg_" + id)
                    .build();

            WorkManager.getInstance(this).enqueue(workRequest);

            mainHandler.post(() -> {
                etMessage.setText("");
                Toast.makeText(this, R.string.message_scheduled, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void checkBlockStatus() {
        executor.execute(() -> {
            boolean isBlocked = AppDatabase.getInstance(this).blockedNumberDao().isBlockedSync(address);
            mainHandler.post(() -> {
                if (isBlocked) {
                    blockedBanner.setVisibility(View.VISIBLE);
                    inputBar.setVisibility(View.GONE);
                } else {
                    blockedBanner.setVisibility(View.GONE);
                    inputBar.setVisibility(View.VISIBLE);
                }
            });
        });

        btnUnblock.setOnClickListener(v -> executor.execute(() -> {
            AppDatabase.getInstance(this).blockedNumberDao().deleteByNumber(address);
            mainHandler.post(() -> {
                blockedBanner.setVisibility(View.GONE);
                inputBar.setVisibility(View.VISIBLE);
                Toast.makeText(this, R.string.number_unblocked, Toast.LENGTH_SHORT).show();
            });
        }));
    }

    private void loadMessages() {
        // Merge requests: if a load is already queued and has not started, it will read the latest data anyway.
        if (!loadQueued.compareAndSet(false, true)) return;

        if (!initialLoadDone && messageAdapter.getItemCount() == 0) {
            mainHandler.removeCallbacks(showLoaderRunnable);
            mainHandler.postDelayed(showLoaderRunnable, LOADER_DELAY_MS);
        }

        executor.execute(() -> {
            // From here on, a new change must trigger one more load, because this one may already have read the old data.
            loadQueued.set(false);

            if (threadId <= 0 && !TextUtils.isEmpty(address)) {
                // Only LOOK UP an existing thread. Telephony.Threads.getOrCreateThreadId() inserted an empty
                // thread row whenever a new chat was opened, and if nothing was sent it showed up as a blank
                // conversation in the list. The thread is created when the first message is sent.
                threadId = SmsHelper.findExistingThreadId(this, address);
            }

            final long currentThreadId = threadId;
            // SMS and MMS together. Reading only SMS made conversations that hold MMS open empty.
            final List<MessageItem> list = currentThreadId > 0
                    ? SmsHelper.getConversationMessages(this, currentThreadId)
                    : new ArrayList<>();

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                hideLoader();
                int previousCount = messageAdapter.getItemCount();
                messageAdapter.setMessages(list);
                // Scroll to the newest message on first display or when messages were added. A background
                // refresh no longer jumps the user away from older messages they are reading.
                if (!list.isEmpty() && (!initialLoadDone || list.size() > previousCount)) {
                    rvMessages.scrollToPosition(list.size() - 1);
                }
                initialLoadDone = true;
            });

            // Marked read after the messages are handed to the UI, so it no longer delays the first display.
            if (currentThreadId > 0) {
                SmsHelper.markThreadRead(this, currentThreadId);
            }
        });
    }

    private void showLoaderIfNothingDisplayed() {
        if (progressLoading != null && !initialLoadDone && messageAdapter.getItemCount() == 0) {
            progressLoading.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoader() {
        mainHandler.removeCallbacks(showLoaderRunnable);
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
    }

    private void registerSmsObserver() {
        smsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // In the background, skip it: onResume() reloads on return. While visible, debounce bursts
                // (sending a multipart SMS or marking messages read fires several notifications) into one reload.
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                mainHandler.removeCallbacks(observerReloadRunnable);
                mainHandler.postDelayed(observerReloadRunnable, OBSERVER_DEBOUNCE_MS);
            }
        };
        try {
            getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://mms"), true, smsObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://mms-sms/"), true, smsObserver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBlockStatus();
        loadMessages();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(showLoaderRunnable);
        mainHandler.removeCallbacks(observerReloadRunnable);
        if (smsObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(smsObserver);
            } catch (Exception ignored) {}
        }
    }
}
