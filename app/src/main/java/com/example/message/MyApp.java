package com.example.message;

import android.app.Application;
import com.example.message.utils.LocaleHelper;
import com.example.message.preferences.PreferenceManager;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        String lang = new PreferenceManager(this).getLanguage();
        LocaleHelper.setLocale(this, lang);
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        PreferenceManager prefs = new PreferenceManager(base);
        super.attachBaseContext(LocaleHelper.setLocale(base, prefs.getLanguage()));
    }
}
