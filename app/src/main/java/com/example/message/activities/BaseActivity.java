package com.example.message.activities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.message.preferences.PreferenceManager;
import com.example.message.utils.FontHelper;
import com.example.message.utils.LocaleHelper;

public abstract class BaseActivity extends AppCompatActivity {

    private String currentLanguageCode;

    @Override
    protected void attachBaseContext(Context newBase) {
        Context langContext = LocaleHelper.onAttach(newBase);
        Context fontContext = FontHelper.applyFontScale(langContext);
        super.attachBaseContext(fontContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        currentLanguageCode = PreferenceManager.getInstance(this).getLanguageCode();
        applyCurrentTheme();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // CHANGE: Apply the new language throughout the application when returning from language settings.
        String savedLang = PreferenceManager.getInstance(this).getLanguageCode();
        if (currentLanguageCode != null && !currentLanguageCode.equals(savedLang)) {
            recreate();
        }
    }

    protected void applyCurrentTheme() {
        int themeMode = PreferenceManager.getInstance(this).getThemeMode();
        switch (themeMode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
