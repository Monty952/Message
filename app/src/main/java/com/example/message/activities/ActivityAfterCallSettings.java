package com.example.message.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.message.R;
import com.example.message.preferences.PreferenceManager;

public class ActivityAfterCallSettings extends BaseActivity {

    private Toolbar toolbar;
    private LinearLayout llToggleRow;
    private ImageView ivToggle;
    private Button btnGrantPermission;

    private PreferenceManager prefManager;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_after_call_settings);

        prefManager = new PreferenceManager(this);

        initViews();
        setupToolbar();
        setupPermissionLauncher();
        updateToggleState();
        checkPermissions();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        llToggleRow = findViewById(R.id.llToggleRow);
        ivToggle = findViewById(R.id.ivToggle);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> checkPermissions()
        );
    }

    private void updateToggleState() {
        boolean enabled = prefManager.isAfterCallEnabled();
        ivToggle.setImageResource(enabled ? R.drawable.btn_on : R.drawable.btn_off);
    }

    private void checkPermissions() {
        boolean phoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean callLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;

        if (phoneState && callLog) {
            btnGrantPermission.setVisibility(View.GONE);
        } else {
            btnGrantPermission.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        llToggleRow.setOnClickListener(v -> {
            boolean newState = !prefManager.isAfterCallEnabled();
            prefManager.setAfterCallEnabled(newState);
            updateToggleState();
            Toast.makeText(this, newState ? R.string.after_call_feature_enabled : R.string.after_call_feature_disabled, Toast.LENGTH_SHORT).show();
        });

        btnGrantPermission.setOnClickListener(v -> {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }
}
