package com.example.message.activities;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.example.message.R;
import com.example.message.preferences.PreferenceManager;

public class ActivityThemes extends BaseActivity {

    private Toolbar toolbar;
    private RadioGroup rgThemes;
    private RadioButton rbLight, rbDark, rbSystem;
    private PreferenceManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_themes);

        prefManager = new PreferenceManager(this);

        initViews();
        setupToolbar();
        setupThemeOptions();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rgThemes = findViewById(R.id.rgThemes);
        rbLight = findViewById(R.id.rbLight);
        rbDark = findViewById(R.id.rbDark);
        rbSystem = findViewById(R.id.rbSystem);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            // CHANGE: Retrieve the selected language and apply the corresponding localized resources.
            toolbar.setTitle(R.string.themes);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupThemeOptions() {
        int currentTheme = prefManager.getTheme(); // 0=light, 1=dark, 2=system
        if (currentTheme == 0) {
            rbLight.setChecked(true);
        } else if (currentTheme == 1) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        rgThemes.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedTheme;
            int mode;
            if (checkedId == R.id.rbLight) {
                selectedTheme = 0;
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rbDark) {
                selectedTheme = 1;
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                selectedTheme = 2;
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }

            prefManager.setTheme(selectedTheme);
            AppCompatDelegate.setDefaultNightMode(mode);
        });
    }
}
