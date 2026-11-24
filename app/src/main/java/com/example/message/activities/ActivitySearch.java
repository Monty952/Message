package com.example.message.activities;

import android.content.Intent;
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

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import com.example.message.R;
import com.example.message.adapters.ConversationAdapter;
import com.example.message.models.Conversation;
import com.example.message.utils.SmsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivitySearch extends BaseActivity {

    private ImageView ivBack, ivClear;
    private EditText etSearch;
    private RecyclerView rvResults;
    private LinearLayout emptyState;

    private ConversationAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerView();
        setupListeners();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        ivClear = findViewById(R.id.ivClear);
        etSearch = findViewById(R.id.etSearch);
        rvResults = findViewById(R.id.rvResults);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this);
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvResults.addItemDecoration(dividerItemDecoration);
        rvResults.setAdapter(adapter);

        adapter.setListener(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onClick(Conversation conversation) {
                Intent intent = new Intent(ActivitySearch.this, ActivityChat.class);
                intent.putExtra("thread_id", conversation.getThreadId());
                intent.putExtra("address", conversation.getAddress());
                intent.putExtra("contact_name", conversation.getContactName());
                startActivity(intent);
            }

            @Override
            public void onLongClick(Conversation conversation) {}
        });
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        ivClear.setOnClickListener(v -> {
            etSearch.setText("");
            adapter.setConversations(new ArrayList<>());
            emptyState.setVisibility(View.GONE);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().trim() : "";
                ivClear.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) {
                    mainHandler.removeCallbacks(searchRunnable);
                }

                if (TextUtils.isEmpty(query)) {
                    adapter.setConversations(new ArrayList<>());
                    emptyState.setVisibility(View.GONE);
                } else {
                    searchRunnable = () -> performSearch(query);
                    mainHandler.postDelayed(searchRunnable, 300);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performSearch(String query) {
        executor.execute(() -> {
            List<Conversation> results = SmsHelper.searchConversations(this, query);
            mainHandler.post(() -> {
                adapter.setConversations(results);
                if (results.isEmpty()) {
                    rvResults.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    rvResults.setVisibility(View.VISIBLE);
                    emptyState.setVisibility(View.GONE);
                }
            });
        });
    }
}
