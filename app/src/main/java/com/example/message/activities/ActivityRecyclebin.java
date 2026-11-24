package com.example.message.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import com.example.message.R;
import com.example.message.adapters.RecycleBinAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.RecycleBinEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityRecyclebin extends BaseActivity {

    private Toolbar toolbar;
    private RecyclerView rvBin;
    private LinearLayout llEmpty;
    private Button btnEmptyBin;

    private RecycleBinAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recyclebin);

        initViews();
        setupToolbar();
        setupRecyclerView();
        observeBin();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvBin = findViewById(R.id.rvBin);
        llEmpty = findViewById(R.id.llEmpty);
        btnEmptyBin = findViewById(R.id.btnEmptyBin);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        adapter = new RecycleBinAdapter(this, new RecycleBinAdapter.OnBinActionListener() {
            @Override
            public void onRestore(RecycleBinEntity entity) {
                restoreConversation(entity);
            }

            @Override
            public void onDeletePermanently(RecycleBinEntity entity) {
                confirmDeletePermanently(entity);
            }
        });
        rvBin.setLayoutManager(new LinearLayoutManager(this));
        rvBin.setAdapter(adapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvBin.addItemDecoration(dividerItemDecoration);
    }

    private void observeBin() {
        AppDatabase.getInstance(this).recycleBinDao().getAllLive().observe(this, list -> {
            adapter.setList(list);
            if (list == null || list.isEmpty()) {
                rvBin.setVisibility(View.GONE);
                llEmpty.setVisibility(View.VISIBLE);
                btnEmptyBin.setVisibility(View.GONE);
            } else {
                rvBin.setVisibility(View.VISIBLE);
                llEmpty.setVisibility(View.GONE);
                btnEmptyBin.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupListeners() {
        btnEmptyBin.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.are_you_sure_permanently_delete)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        executor.execute(() -> {
                            java.util.List<RecycleBinEntity> all = AppDatabase.getInstance(this).recycleBinDao().getAllSync();
                            if (all != null) {
                                for (RecycleBinEntity e : all) {
                                    com.example.message.utils.SmsHelper.deleteThread(this, e.getThreadId());
                                }
                            }
                            AppDatabase.getInstance(this).recycleBinDao().deleteAll();
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void restoreConversation(RecycleBinEntity entity) {
        executor.execute(() -> {
            AppDatabase.getInstance(this).recycleBinDao().deleteByThreadId(entity.getThreadId());
        });
        Toast.makeText(this, R.string.restored_to_inbox, Toast.LENGTH_SHORT).show();
    }

    private void confirmDeletePermanently(RecycleBinEntity entity) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.are_you_sure_permanently_delete)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    executor.execute(() -> {
                        AppDatabase.getInstance(this).recycleBinDao().deleteByThreadId(entity.getThreadId());
                        com.example.message.utils.SmsHelper.deleteThread(this, entity.getThreadId());
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
