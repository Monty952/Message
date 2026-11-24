package com.example.message.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import com.example.message.R;
import com.example.message.adapters.ContactAdapter;
import com.example.message.models.ContactItem;
import com.example.message.utils.ContactHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivitySearchContact extends BaseActivity {

    private ImageView ivBack;
    private EditText etRecipient;
    private TextView btnStartChat;
    private RecyclerView rvContacts;
    private LinearLayout emptyState;

    private ContactAdapter adapter;
    private List<ContactItem> allContacts = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String forwardBody = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_contact);

        Intent intent = getIntent();
        if (intent != null) {
            forwardBody = intent.getStringExtra("forward_body");
            if (TextUtils.isEmpty(forwardBody)) {
                forwardBody = intent.getStringExtra("sms_body");
            }
            if (TextUtils.isEmpty(forwardBody)) {
                forwardBody = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if ("sms".equalsIgnoreCase(scheme) || "smsto".equalsIgnoreCase(scheme)
                        || "mms".equalsIgnoreCase(scheme) || "mmsto".equalsIgnoreCase(scheme)) {
                    String number = data.getSchemeSpecificPart();
                    if (number != null) {
                        if (number.contains("?")) {
                            number = number.substring(0, number.indexOf("?"));
                        }
                        if (!number.trim().isEmpty()) {
                            openChat(number.trim(), number.trim());
                            return;
                        }
                    }
                }
            }
        }

        initViews();
        setupRecyclerView();
        setupListeners();
        loadContacts();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        etRecipient = findViewById(R.id.etRecipient);
        btnStartChat = findViewById(R.id.btnStartChat);
        rvContacts = findViewById(R.id.rvContacts);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(this, contact -> openChat(contact.getNumber(), contact.getName()));
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvContacts.addItemDecoration(dividerItemDecoration);
        rvContacts.setAdapter(adapter);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnStartChat.setOnClickListener(v -> {
            String input = etRecipient.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(this, R.string.please_enter_valid_phone, Toast.LENGTH_SHORT).show();
                return;
            }
            openChat(input, input);
        });

        etRecipient.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s != null ? s.toString().trim() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadContacts() {
        executor.execute(() -> {
            List<ContactItem> list = ContactHelper.getContacts(this);
            allContacts = list != null ? list : new ArrayList<>();
            mainHandler.post(() -> {
                adapter.setContacts(allContacts);
                if (allContacts.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    emptyState.setVisibility(View.GONE);
                }
            });
        });
    }

    private void filterContacts(String query) {
        if (TextUtils.isEmpty(query)) {
            adapter.setContacts(allContacts);
            emptyState.setVisibility(allContacts.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        List<ContactItem> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (ContactItem item : allContacts) {
            boolean matchesName = item.getName() != null && item.getName().toLowerCase().contains(lowerQuery);
            boolean matchesNum = item.getNumber() != null && item.getNumber().contains(query);
            if (matchesName || matchesNum) {
                filtered.add(item);
            }
        }
        adapter.setContacts(filtered);
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openChat(String number, String name) {
        Intent intent = new Intent(this, ActivityChat.class);
        intent.putExtra("address", number);
        intent.putExtra("contact_name", name);
        intent.putExtra("thread_id", -1L);
        if (forwardBody != null) {
            intent.putExtra("forward_body", forwardBody);
        }
        try {
            if (getIntent() != null) {
                Uri stream = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) {
                    intent.putExtra(Intent.EXTRA_STREAM, stream);
                }
            }
        } catch (Exception ignored) {}
        startActivity(intent);
        finish();
    }
}
