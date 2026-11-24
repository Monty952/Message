package com.example.message.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import com.example.message.preferences.PreferenceManager;

import java.util.Locale;

public class LocaleHelper {

    // CHANGE: Retrieve the selected language and apply the corresponding localized resources.
    public static Context onAttach(Context context) {
        String lang = PreferenceManager.getInstance(context).getLanguageCode();
        return setLocale(context, lang);
    }

    // CHANGE: Retrieve the selected language and apply the corresponding localized resources.
    public static Context setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResources(context, locale);
        } else {
            return updateResourcesLegacy(context, locale);
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResources(Context context, Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        LocaleList localeList = new LocaleList(locale);
        LocaleList.setDefault(localeList);
        configuration.setLocales(localeList);
        try {
            context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        } catch (Exception ignored) {}
        return context.createConfigurationContext(configuration);
    }

    @SuppressWarnings("deprecation")
    private static Context updateResourcesLegacy(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }
}
