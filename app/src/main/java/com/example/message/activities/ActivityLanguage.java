package com.example.message.activities;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.message.MainActivity;
import com.example.message.R;
import com.example.message.adapters.LanguageAdapter;
import com.example.message.models.LanguageItem;
import com.example.message.preferences.PreferenceManager;
import com.example.message.utils.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

public class ActivityLanguage extends BaseActivity {

    private PreferenceManager preferenceManager;
    private LanguageAdapter adapter;
    private LanguageItem selectedLanguage;

    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // After role result, request runtime permissions
                requestRuntimePermissions();
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                // Mark onboarding done regardless of permission outcome
                preferenceManager.setOnboardingDone(true);
                goToMain();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferenceManager = new PreferenceManager(this);

        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);

        // CHANGE: Check whether the initial language selection has already been completed.
        // If completed, skip ActivityLanguage during normal application startup.
        if (!fromSettings && preferenceManager.isLanguageSelected()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_language);

        // ivBack: only show if from settings
        ImageView ivBack = findViewById(R.id.ivBack);
        if (fromSettings) {
            ivBack.setVisibility(View.VISIBLE);
            ivBack.setOnClickListener(v -> finish());
        } else {
            ivBack.setVisibility(View.GONE);
        }

        // Build language list
        List<LanguageItem> languages = new ArrayList<>();
        languages.add(new LanguageItem("en", "English", "English", R.drawable.l_english));
        languages.add(new LanguageItem("es", "Spanish", "Español", R.drawable.l_spenish));
        languages.add(new LanguageItem("fr", "French", "Français", R.drawable.l_french));
        languages.add(new LanguageItem("pt", "Portuguese", "Português", R.drawable.l_portu));
        languages.add(new LanguageItem("ru", "Russian", "Русский", R.drawable.l_russian));
        languages.add(new LanguageItem("hi", "Hindi", "हिन्दी", R.drawable.l_hindi));
        languages.add(new LanguageItem("de", "German", "Deutsch", R.drawable.l_german));
        languages.add(new LanguageItem("it", "Italian", "Italiano", R.drawable.l_itali));
        languages.add(new LanguageItem("bn", "Bengali", "বাংলা", R.drawable.l_bengali));
        languages.add(new LanguageItem("tr", "Turkish", "Türkçe", R.drawable.l_turki));
        languages.add(new LanguageItem("ro", "Romanian", "Română", R.drawable.l_romania));

        // Determine default selection
        String savedLang = preferenceManager.getLanguage();
        if (savedLang == null || savedLang.isEmpty()) savedLang = "en";
        int defaultPos = 0;
        for (int i = 0; i < languages.size(); i++) {
            if (languages.get(i).code.equals(savedLang)) {
                defaultPos = i;
                break;
            }
        }
        selectedLanguage = languages.get(defaultPos);

        // Setup RecyclerView
        RecyclerView rvLanguages = findViewById(R.id.rvLanguages);
        rvLanguages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LanguageAdapter(this, languages, defaultPos, item -> {
            selectedLanguage = item;
        });
        rvLanguages.setAdapter(adapter);

        // Done button
        TextView btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(v -> onDoneClicked());
    }

    private void onDoneClicked() {
        if (selectedLanguage == null) {
            selectedLanguage = adapter.getSelectedItem();
        }
        if (selectedLanguage == null) return;

        // Save language and apply locale
        preferenceManager.setLanguage(selectedLanguage.code);
        LocaleHelper.setLocale(this, selectedLanguage.code);

        // CHANGE: Save the initial language selection status.
        // This prevents ActivityLanguage from appearing on every subsequent app launch.
        preferenceManager.setLanguageSelected(true);

        // CHANGE: Allow ActivityLanguage to open from Settings
        // without resetting the initial onboarding completion status.
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        if (fromSettings) {
            finish();
            return;
        }

        if (!preferenceManager.isOnboardingDone()) {
            showPermissionDialog();
        } else {
            goToMain();
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_message)
                .setPositiveButton(R.string.allow, (dialog, which) -> {
                    requestSmsRoleOrPermissions();
                })
                .setNegativeButton(R.string.skip, (dialog, which) -> {
                    preferenceManager.setOnboardingDone(true);
                    goToMain();
                })
                .setCancelable(false)
                .show();
    }

    private void requestSmsRoleOrPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                Intent roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                roleRequestLauncher.launch(roleIntent);
                return;
            }
        } else {
            // For pre-Q: check using Telephony
            String defaultPkg = Telephony.Sms.getDefaultSmsPackage(this);
            if (!getPackageName().equals(defaultPkg)) {
                Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                roleRequestLauncher.launch(intent);
                return;
            }
        }
        // Already default SMS app, just request permissions
        requestRuntimePermissions();
    }

    private void requestRuntimePermissions() {
        List<String> perms = new ArrayList<>();
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.READ_SMS);
        }
        if (checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.SEND_SMS);
        }
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.RECEIVE_SMS);
        }
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_MMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.RECEIVE_MMS);
        }
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.READ_CONTACTS);
        }
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!perms.isEmpty()) {
            permissionLauncher.launch(perms.toArray(new String[0]));
        } else {
            preferenceManager.setOnboardingDone(true);
            goToMain();
        }
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
