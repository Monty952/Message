package com.example.message;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import com.example.message.activities.ActivityAfterCallSettings;
import com.example.message.activities.ActivityArchive;
import com.example.message.activities.ActivityBackup;
import com.example.message.activities.ActivityBlockNumber;
import com.example.message.activities.ActivityChat;
import com.example.message.activities.ActivityLanguage;
import com.example.message.activities.ActivityRecyclebin;
import com.example.message.activities.ActivitySchedule;
import com.example.message.activities.ActivitySearch;
import com.example.message.activities.ActivitySearchContact;
import com.example.message.activities.ActivitySetting;
import com.example.message.activities.ActivitySwipeAction;
import com.example.message.activities.BaseActivity;
import com.example.message.adapters.ConversationAdapter;
import com.example.message.database.AppDatabase;
import com.example.message.database.ArchiveEntity;
import com.example.message.database.RecycleBinEntity;
import com.example.message.models.Conversation;
import com.example.message.preferences.PreferenceManager;
import com.example.message.utils.ConversationListCache;
import com.example.message.utils.SmsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private ImageView ivMenu, ivSearch;
    private TextView tvTitle;
    private LinearLayout bannerDefaultSms;
    private Button btnSetDefault;
    private RecyclerView rvConversations;
    private LinearLayout emptyState;
    private ProgressBar progressLoading;
    private FloatingActionButton fabNewChat;

    private ConversationAdapter adapter;
    private PreferenceManager prefManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ContentObserver smsObserver;
    private ActivityResultLauncher<Intent> defaultSmsLauncher;

    /** Wait before showing the loader, so it never flashes when loading is quick. */
    private static final long LOADER_DELAY_MS = 300;
    /** Batches the bursts of change notifications that SMS database writes produce into one reload. */
    private static final long OBSERVER_DEBOUNCE_MS = 300;

    /**
     * Last list shown, kept for the life of the process. Shown immediately when this screen is
     * recreated (rotation, theme or language change, reopening the app) while a fresh load runs.
     * Only read and written on the main thread.
     */
    private static List<Conversation> lastLoadedConversations;

    /** True while a load is queued but not yet started. Extra requests in that window are merged into it. */
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private final Runnable showLoaderRunnable = this::showLoaderIfNothingDisplayed;
    private final Runnable observerReloadRunnable = this::loadConversations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefManager = new PreferenceManager(this);

        if (!prefManager.isOnboardingDone()) {
            startActivity(new Intent(this, ActivityLanguage.class));
            finish();
            return;
        }

        setContentView(R.layout.
                activity_main);

        initViews();
        setupDefaultSmsLauncher();
        setupNavigation();
        setupRecyclerView();
        setupListeners();
        setupSwipeActions();
        registerSmsObserver();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        ivMenu = findViewById(R.id.ivMenu);
        ivSearch = findViewById(R.id.ivSearch);
        tvTitle = findViewById(R.id.tvTitle);
        bannerDefaultSms = findViewById(R.id.bannerDefaultSms);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        rvConversations = findViewById(R.id.rvConversations);
        emptyState = findViewById(R.id.emptyState);
        progressLoading = findViewById(R.id.progressLoading);
        fabNewChat = findViewById(R.id.fabNewChat);
    }

    private void setupDefaultSmsLauncher() {
        defaultSmsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkDefaultSms()
        );
    }

    private void setupNavigation() {
        if (navView != null) {
            navView.setItemIconTintList(null);
            navView.setNavigationItemSelectedListener(this);
        }

        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            });
        }
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_line);
        if (dividerDrawable != null) {
            dividerItemDecoration.setDrawable(dividerDrawable);
        }
        rvConversations.addItemDecoration(dividerItemDecoration);

        // Show the last loaded list straight away (no loader). onResume() then refreshes it in the background.
        if (lastLoadedConversations != null) {
            adapter.setConversations(new ArrayList<>(lastLoadedConversations));
            updateEmptyState();
        } else {
            // The process was not running: use the copy saved by the last load instead of an empty screen.
            showSavedConversations();
        }

        adapter.setListener(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onClick(Conversation conversation) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(conversation.getThreadId());
                    updateToolbarSelectionState();
                } else {
                    Intent intent = new Intent(MainActivity.this, ActivityChat.class);
                    intent.putExtra("thread_id", conversation.getThreadId());
                    intent.putExtra("address", conversation.getAddress());
                    intent.putExtra("contact_name", conversation.getContactName());
                    startActivity(intent);
                }
            }

            @Override
            public void onLongClick(Conversation conversation) {
                if (!adapter.isSelectionMode()) {
                    adapter.setSelectionMode(true);
                }
                adapter.toggleSelection(conversation.getThreadId());
                updateToolbarSelectionState();
            }
        });
    }

    private void updateToolbarSelectionState() {
        if (adapter.isSelectionMode()) {
            int count = adapter.getSelectedCount();
            if (count == 0) {
                adapter.setSelectionMode(false);
                tvTitle.setText(R.string.messages);
            } else {
                tvTitle.setText(getString(R.string.count_selected, count));
            }
        } else {
            tvTitle.setText(R.string.messages);
        }
    }

    private void setupListeners() {
        ivSearch.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ActivitySearch.class)));

        if (btnSetDefault != null) {
            btnSetDefault.setOnClickListener(v -> requestDefaultSmsRole());
        }

        fabNewChat.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ActivitySearchContact.class)));
    }

    private void setupSwipeActions() {
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
                    String action = null;
                    if (direction == ItemTouchHelper.RIGHT) {
                        action = prefManager.getSwipeRightAction();
                    } else if (direction == ItemTouchHelper.LEFT) {
                        action = prefManager.getSwipeLeftAction();
                    }
                    if (action != null) {
                        // Archive and delete remove the row when the list reloads. For any other action the row
                        // stays, so put the swiped-away view back now: the diff-based refresh only rebinds rows
                        // whose content changed.
                        if (!"archive".equalsIgnoreCase(action) && !"delete".equalsIgnoreCase(action)) {
                            adapter.notifyItemChanged(pos);
                        }
                        handleSwipeAction(action, conv);
                    }
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvConversations);
    }

    private void handleSwipeAction(String action, Conversation conv) {
        if ("archive".equalsIgnoreCase(action)) {
            executor.execute(() -> {
                AppDatabase.getInstance(this).archiveDao().insert(new ArchiveEntity(conv.getThreadId(), conv.getAddress(), conv.getSnippet(), System.currentTimeMillis()));
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.archived_successfully, Toast.LENGTH_SHORT).show();
                    loadConversations();
                });
            });
        } else if ("delete".equalsIgnoreCase(action)) {
            executor.execute(() -> {
                AppDatabase.getInstance(this).recycleBinDao().insert(new RecycleBinEntity(conv.getThreadId(), conv.getAddress(), conv.getSnippet(), System.currentTimeMillis()));
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.moved_to_recycle_bin, Toast.LENGTH_SHORT).show();
                    loadConversations();
                });
            });
        } else if ("read".equalsIgnoreCase(action)) {
            executor.execute(() -> {
                SmsHelper.markThreadRead(this, conv.getThreadId());
                mainHandler.post(this::loadConversations);
            });
        } else {
            loadConversations();
        }
    }

    private void registerSmsObserver() {
        smsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // The observer stays registered while this screen is in the background (e.g. a chat is open
                // and marks messages read). Reloading then is wasted work that delays the reload onResume()
                // does on return. While visible, debounce bursts of notifications into one reload.
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                mainHandler.removeCallbacks(observerReloadRunnable);
                mainHandler.postDelayed(observerReloadRunnable, OBSERVER_DEBOUNCE_MS);
            }
        };
        try {
            getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://mms"), true, smsObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://mms-sms/"), true, smsObserver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkDefaultSms() {
        boolean isDefault;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            isDefault = roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_SMS);
        } else {
            String defaultPackage = Telephony.Sms.getDefaultSmsPackage(this);
            isDefault = getPackageName().equals(defaultPackage);
        }

        if (bannerDefaultSms != null) {
            bannerDefaultSms.setVisibility(isDefault ? View.GONE : View.VISIBLE);
        }
    }

    private void requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                defaultSmsLauncher.launch(intent);
                return;
            }
        }
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        defaultSmsLauncher.launch(intent);
    }

    private void loadConversations() {
        if (adapter == null) return;

        // Merge requests: if a load is already queued and has not started, it will read the latest data
        // anyway. Without this, every onResume and observer change queued another full load on the
        // single-thread executor, and the screen waited for the whole backlog.
        if (!reloadQueued.compareAndSet(false, true)) return;

        if (adapter.getItemCount() == 0) {
            mainHandler.removeCallbacks(showLoaderRunnable);
            mainHandler.postDelayed(showLoaderRunnable, LOADER_DELAY_MS);
        }

        executor.execute(() -> {
            // From here on, a new change must trigger one more load, because this one may already have read the old data.
            reloadQueued.set(false);

            List<Conversation> loaded;
            try {
                // getConversations() already excludes archived and recycle-bin threads, so the separate archive
                // query and filtering loop that used to run here was a duplicate.
                loaded = SmsHelper.getConversations(this);
            } catch (Exception e) {
                e.printStackTrace();
                loaded = null;
            }

            final List<Conversation> result = loaded;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                hideLoader();
                if (result != null) {
                    lastLoadedConversations = new ArrayList<>(result);
                    adapter.setConversations(result);
                }
                // On failure, whatever is already displayed stays on screen; otherwise the empty state shows.
                updateEmptyState();
            });

            // Saved for the next start of the app (see showSavedConversations()). Done after the list is handed to
            // the screen so it never delays it. The list is only read here, never changed.
            if (result != null) {
                ConversationListCache.write(this, result);
            }
        });
    }

    /**
     * After the app process ended, the screen stayed empty (then showed a loader) until the whole list was rebuilt
     * from the SMS database and contacts. Google Messages shows its list at once from its own database. This does
     * the same with the copy saved by the last load: it runs first on the executor, and the load that onResume()
     * starts replaces it right after, redrawing only the rows that changed.
     */
    private void showSavedConversations() {
        // Never show saved message previews without permission to read SMS.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        executor.execute(() -> {
            final List<Conversation> saved = ConversationListCache.read(this);
            if (saved == null || saved.isEmpty()) return;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                // Only fill an empty screen; never replace a list that is already shown.
                if (adapter.getItemCount() > 0) return;
                hideLoader();
                adapter.setConversations(saved);
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        rvConversations.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showLoaderIfNothingDisplayed() {
        if (progressLoading != null && adapter != null && adapter.getItemCount() == 0) {
            emptyState.setVisibility(View.GONE);
            progressLoading.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoader() {
        mainHandler.removeCallbacks(showLoaderRunnable);
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_archive) {
            startActivity(new Intent(this, ActivityArchive.class));
        } else if (id == R.id.nav_blocked) {
            startActivity(new Intent(this, ActivityBlockNumber.class));
        } else if (id == R.id.nav_scheduled) {
            startActivity(new Intent(this, ActivitySchedule.class));
        } else if (id == R.id.nav_backup) {
            startActivity(new Intent(this, ActivityBackup.class));
        } else if (id == R.id.nav_swipe) {
            startActivity(new Intent(this, ActivitySwipeAction.class));
        } else if (id == R.id.nav_recycle_bin) {
            startActivity(new Intent(this, ActivityRecyclebin.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, ActivitySetting.class));
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkDefaultSms();
        loadConversations();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (adapter != null && adapter.isSelectionMode()) {
            adapter.clearSelection();
            updateToolbarSelectionState();
        } else {
            super.onBackPressed();
        }
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
