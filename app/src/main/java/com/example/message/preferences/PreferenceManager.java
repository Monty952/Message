package com.example.message.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "message_preferences";
    private static PreferenceManager instance;
    private final SharedPreferences prefs;

    // Keys
    public static final String KEY_ONBOARDING_DONE = "key_onboarding_done";
    public static final String KEY_LANGUAGE_CODE = "key_language_code";
    public static final String KEY_LANGUAGE_SELECTED = "key_language_selected";
    public static final String KEY_THEME_MODE = "key_theme_mode"; // 0: Light, 1: Dark, 2: System Default
    public static final String KEY_FONT_SIZE_INDEX = "key_font_size_index"; // 0: Small, 1: Normal, 2: Large, 3: Extra Large
    public static final String KEY_RIGHT_SWIPE_ACTION = "key_right_swipe_action"; // archive, delete, read, unread, block, none
    public static final String KEY_LEFT_SWIPE_ACTION = "key_left_swipe_action";
    public static final String KEY_AFTER_CALL_ENABLED = "key_after_call_enabled";
    public static final String KEY_INSTANT_REPLY_ENABLED = "key_instant_reply_enabled";
    public static final String KEY_CHAT_BUBBLES_ENABLED = "key_chat_bubbles_enabled";
    public static final String KEY_LAST_BACKUP = "key_last_backup";

    // Swipe Action Constants
    public static final String ACTION_ARCHIVE = "archive";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_MARK_READ = "read";
    public static final String ACTION_MARK_UNREAD = "unread";
    public static final String ACTION_BLOCK = "block";
    public static final String ACTION_NONE = "none";

    public PreferenceManager(Context context) {
        Context ctx = (context != null && context.getApplicationContext() != null) ? context.getApplicationContext() : context;
        prefs = (ctx != null) ? ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) : null;
    }

    public static synchronized PreferenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferenceManager(context);
        }
        return instance;
    }

    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    // CHANGE: Save the initial language selection status.
    // This prevents ActivityLanguage from appearing on every subsequent app launch.
    public boolean isLanguageSelected() {
        return prefs != null && (prefs.getBoolean(KEY_LANGUAGE_SELECTED, false) || prefs.getBoolean(KEY_ONBOARDING_DONE, false));
    }

    public void setLanguageSelected(boolean selected) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_LANGUAGE_SELECTED, selected).apply();
        }
    }

    public String getLanguageCode() {
        return prefs.getString(KEY_LANGUAGE_CODE, "en");
    }

    public void setLanguageCode(String code) {
        prefs.edit().putString(KEY_LANGUAGE_CODE, code).apply();
    }

    public String getLanguage() {
        return getLanguageCode();
    }

    public void setLanguage(String code) {
        setLanguageCode(code);
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, 2); // default 2: System Default
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getTheme() {
        return getThemeMode();
    }

    public void setTheme(int mode) {
        setThemeMode(mode);
    }

    public int getFontSizeIndex() {
        return prefs.getInt(KEY_FONT_SIZE_INDEX, 1); // default 1: Normal
    }

    public void setFontSizeIndex(int index) {
        prefs.edit().putInt(KEY_FONT_SIZE_INDEX, index).apply();
    }

    public int getFontSize() {
        return getFontSizeIndex();
    }

    public void setFontSize(int index) {
        setFontSizeIndex(index);
    }

    public float getFontScale() {
        switch (getFontSizeIndex()) {
            case 0: return 0.85f; // Small
            case 2: return 1.15f; // Large
            case 3: return 1.30f; // Extra Large
            case 1:
            default:
                return 1.0f; // Normal
        }
    }

    public String getRightSwipeAction() {
        return prefs.getString(KEY_RIGHT_SWIPE_ACTION, ACTION_ARCHIVE);
    }

    public void setRightSwipeAction(String action) {
        prefs.edit().putString(KEY_RIGHT_SWIPE_ACTION, action).apply();
    }

    public String getSwipeRightAction() {
        return getRightSwipeAction();
    }

    public void setSwipeRightAction(String action) {
        setRightSwipeAction(action);
    }

    public String getLeftSwipeAction() {
        return prefs.getString(KEY_LEFT_SWIPE_ACTION, ACTION_DELETE);
    }

    public void setLeftSwipeAction(String action) {
        prefs.edit().putString(KEY_LEFT_SWIPE_ACTION, action).apply();
    }

    public String getSwipeLeftAction() {
        return getLeftSwipeAction();
    }

    public void setSwipeLeftAction(String action) {
        setLeftSwipeAction(action);
    }

    public boolean isAfterCallEnabled() {
        return prefs.getBoolean(KEY_AFTER_CALL_ENABLED, true);
    }

    public void setAfterCallEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AFTER_CALL_ENABLED, enabled).apply();
    }

    public boolean isInstantReplyEnabled() {
        return prefs.getBoolean(KEY_INSTANT_REPLY_ENABLED, true);
    }

    public void setInstantReplyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_INSTANT_REPLY_ENABLED, enabled).apply();
    }

    public boolean isChatBubblesEnabled() {
        return prefs == null || prefs.getBoolean(KEY_CHAT_BUBBLES_ENABLED, true);
    }

    public void setChatBubblesEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_CHAT_BUBBLES_ENABLED, enabled).apply();
        }
    }

    public long getLastBackupTime() {
        return prefs.getLong(KEY_LAST_BACKUP, 0L);
    }

    public void setLastBackupTime(long time) {
        prefs.edit().putLong(KEY_LAST_BACKUP, time).apply();
    }
}
