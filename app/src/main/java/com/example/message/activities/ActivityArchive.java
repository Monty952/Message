package com.example.message.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import com.example.message.R;
import com.example.message.adapters.ConversationAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.ArchiveEntity;
import com.example.message.models.Conversation;
import com.example.message.utils.SmsHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityArchive extends BaseActivity {

    private Toolbar toolbar;
    private RecyclerView rvArchive;
    private LinearLayout llEmpty;

    private ConversationAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupSwipe();
        loadArchived();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvArchive = findViewById(R.id.rvArchive);
        llEmpty = findViewById(R.id.llEmpty);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this);
        rvArchive.setLayoutManager(new LinearLayoutManager(this));
        rvArchive.setAdapter(adapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvArchive.addItemDecoration(dividerItemDecoration);

        adapter.setListener(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onClick(Conversation conversation) {
                Intent intent = new Intent(ActivityArchive.this, ActivityChat.class);
                intent.putExtra("thread_id", conversation.getThreadId());
                intent.putExtra("address", conversation.getAddress());
                intent.putExtra("contact_name", conversation.getContactName());
                startActivity(intent);
            }

            @Override
            public void onLongClick(Conversation conversation) {
                new AlertDialog.Builder(ActivityArchive.this)
                        .setTitle(R.string.unarchive_conversation)
                        .setMessage(R.string.unarchive_conversation_message)
                        .setPositiveButton(R.string.unarchive, (dialog, which) -> unarchive(conversation.getThreadId()))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    private void setupSwipe() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos >= 0 && pos < adapter.getConversations().size()) {
                    Conversation conv = adapter.getConversations().get(pos);
                    unarchive(conv.getThreadId());
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvArchive);
    }

    private void unarchive(long threadId) {
        executor.execute(() -> {
            AppDatabase.getInstance(this).archiveDao().deleteByThreadId(threadId);
            mainHandler.post(() -> {
                Toast.makeText(this, R.string.unarchived, Toast.LENGTH_SHORT).show();
                loadArchived();
            });
        });
    }

    private void loadArchived() {
        executor.execute(() -> {
            // getConversations(this) EXCLUDES archived threads, so filtering its result by the archive table
            // always gave an empty list. Ask for the archived scope directly: archived threads that are not
            // in the recycle bin.
            List<Conversation> archived = SmsHelper.getConversations(this, true, false);

            mainHandler.post(() -> {
                adapter.setConversations(archived);
                if (archived.isEmpty()) {
                    rvArchive.setVisibility(View.GONE);
                    llEmpty.setVisibility(View.VISIBLE);
                } else {
                    rvArchive.setVisibility(View.VISIBLE);
                    llEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadArchived();
    }
}
