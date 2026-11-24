package com.example.message.utils;

import android.content.Context;
import android.content.res.Configuration;

import com.example.message.preferences.PreferenceManager;

public class FontHelper {

    public static Context applyFontScale(Context context) {
        float fontScale = PreferenceManager.getInstance(context).getFontScale();
        Configuration configuration = context.getResources().getConfiguration();
        configuration.fontScale = fontScale;
        return context.createConfigurationContext(configuration);
    }
}
