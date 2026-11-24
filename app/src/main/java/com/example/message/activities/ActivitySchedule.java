package com.example.message.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;
import androidx.work.WorkManager;

import com.example.message.R;
import com.example.message.adapters.ScheduleAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.ScheduledMessageEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivitySchedule extends BaseActivity {

    private Toolbar toolbar;
    private RecyclerView rvScheduled;
    private LinearLayout llEmpty;

    private ScheduleAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        initViews();
        setupToolbar();
        setupRecyclerView();
        observeSchedule();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvScheduled = findViewById(R.id.rvScheduled);
        llEmpty = findViewById(R.id.llEmpty);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        adapter = new ScheduleAdapter(this, this::confirmDeleteSchedule);
        rvScheduled.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvScheduled.addItemDecoration(dividerItemDecoration);
        rvScheduled.setAdapter(adapter);
    }

    private void observeSchedule() {
        AppDatabase.getInstance(this).scheduledMessageDao().getAllLive().observe(this, list -> {
            adapter.setList(list);
            if (list == null || list.isEmpty()) {
                rvScheduled.setVisibility(View.GONE);
                llEmpty.setVisibility(View.VISIBLE);
            } else {
                rvScheduled.setVisibility(View.VISIBLE);
                llEmpty.setVisibility(View.GONE);
            }
        });
    }

    private void confirmDeleteSchedule(ScheduledMessageEntity entity) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_scheduled_message)
                .setMessage(R.string.cancel_scheduled_message_confirm)
                .setPositiveButton(R.string.cancel_message, (dialog, which) -> {
                    executor.execute(() -> {
                        WorkManager.getInstance(this).cancelAllWorkByTag("schedMsg_" + entity.getId());
                        AppDatabase.getInstance(this).scheduledMessageDao().deleteById(entity.getId());
                    });
                    Toast.makeText(this, R.string.scheduled_message_cancelled, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.keep, null)
                .show();
    }
}
