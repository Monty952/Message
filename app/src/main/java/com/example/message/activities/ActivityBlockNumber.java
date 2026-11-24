package com.example.message.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
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
import com.example.message.adapters.BlockedNumberAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.BlockedNumberEntity;
import com.example.message.utils.ContactHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityBlockNumber extends BaseActivity {

    private Toolbar toolbar;
    private RecyclerView rvBlocked;
    private LinearLayout llEmpty;
    private FloatingActionButton fabAdd;

    private BlockedNumberAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_number);

        initViews();
        setupToolbar();
        setupRecyclerView();
        observeBlockedNumbers();
        setupFab();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvBlocked = findViewById(R.id.rvBlocked);
        llEmpty = findViewById(R.id.llEmpty);
        fabAdd = findViewById(R.id.fabAdd);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        adapter = new BlockedNumberAdapter(this, this::unblockNumber);
        rvBlocked.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvBlocked.addItemDecoration(dividerItemDecoration);
        rvBlocked.setAdapter(adapter);
    }

    private void observeBlockedNumbers() {
        AppDatabase.getInstance(this).blockedNumberDao().getAllLive().observe(this, list -> {
            adapter.setList(list);
            if (list == null || list.isEmpty()) {
                rvBlocked.setVisibility(View.GONE);
                llEmpty.setVisibility(View.VISIBLE);
            } else {
                rvBlocked.setVisibility(View.VISIBLE);
                llEmpty.setVisibility(View.GONE);
            }
        });
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showAddBlockDialog());
    }

    private void showAddBlockDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.enter_phone_number);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(R.string.block_a_number)
                .setView(input)
                .setPositiveButton(R.string.block, (dialog, which) -> {
                    String number = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(number)) {
                        executor.execute(() -> {
                            String name = ContactHelper.getContactName(this, number);
                            BlockedNumberEntity entity = new BlockedNumberEntity(number, name, System.currentTimeMillis());
                            AppDatabase.getInstance(this).blockedNumberDao().insert(entity);
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void unblockNumber(BlockedNumberEntity entity) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unblock_number)
                .setMessage(getString(R.string.unblock_confirmation, entity.getNumber()))
                .setPositiveButton(R.string.unblock, (dialog, which) -> {
                    executor.execute(() -> {
                        AppDatabase.getInstance(this).blockedNumberDao().delete(entity);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
