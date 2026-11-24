package com.example.message.activities;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.example.message.R;
import com.example.message.activities.BaseActivity;
import com.example.message.preferences.PreferenceManager;

public class ActivitySetting extends BaseActivity {

    private PreferenceManager preferenceManager;
    private ImageView ivAfterCallToggle;
    private ImageView ivChatBubblesToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        preferenceManager = new PreferenceManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        ivAfterCallToggle = findViewById(R.id.ivAfterCallToggle);
        ivChatBubblesToggle = findViewById(R.id.ivChatBubblesToggle);

        // Set version
        TextView tvVersion = findViewById(R.id.tvAppVersion);
        tvVersion.setText(R.string.version);

        // Update toggle state
        updateAfterCallToggle();
        updateChatBubblesToggle();

        // Theme
        findViewById(R.id.rowTheme).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityThemes.class)));

        // Font Size
        findViewById(R.id.rowFontSize).setOnClickListener(v -> showFontSizeDialog());

        // Language
        findViewById(R.id.rowLanguage).setOnClickListener(v -> {
            Intent intent = new Intent(this, ActivityLanguage.class);
            intent.putExtra("from_settings", true);
            startActivity(intent);
        });

        // Swipe Actions
        findViewById(R.id.rowSwipeAction).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySwipeAction.class)));

        // Scheduled Messages
        findViewById(R.id.rowSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySchedule.class)));

        // Blocked Numbers
        findViewById(R.id.rowBlockedNumbers).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityBlockNumber.class)));

        // Backup & Restore
        findViewById(R.id.rowBackup).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityBackup.class)));

        // After Call Feature - navigate to settings
        findViewById(R.id.rowAfterCall).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityAfterCallSettings.class)));

        // Notification
        findViewById(R.id.rowNotification).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });

        // Chat Bubbles Toggle
        View rowChatBubbles = findViewById(R.id.rowChatBubbles);
        if (rowChatBubbles != null) {
            rowChatBubbles.setOnClickListener(v -> {
                boolean enabled = preferenceManager.isChatBubblesEnabled();
                preferenceManager.setChatBubblesEnabled(!enabled);
                updateChatBubblesToggle();
            });
        }

        // Privacy Policy
        View rowPrivacy = findViewById(R.id.rowPrivacy);
        if (rowPrivacy != null) {
            rowPrivacy.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy")));
                } catch (Exception ignored) {}
            });
        }

        // Share App
        View rowShare = findViewById(R.id.rowShare);
        if (rowShare != null) {
            rowShare.setOnClickListener(v -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out Messages: https://play.google.com/store/apps/details?id=" + getPackageName());
                startActivity(Intent.createChooser(shareIntent, "Share App"));
            });
        }

        // Rate Us
        View rowRateUs = findViewById(R.id.rowRateUs);
        if (rowRateUs != null) {
            rowRateUs.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void updateAfterCallToggle() {
        boolean enabled = preferenceManager.isAfterCallEnabled();
        ivAfterCallToggle.setImageResource(enabled ? R.drawable.btn_on : R.drawable.btn_off);
    }

    private void updateChatBubblesToggle() {
        boolean enabled = preferenceManager.isChatBubblesEnabled();
        if (ivChatBubblesToggle != null) {
            ivChatBubblesToggle.setImageResource(enabled ? R.drawable.btn_on : R.drawable.btn_off);
        }
    }

    private void showFontSizeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_font);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        SeekBar seekBar = dialog.findViewById(R.id.seekBarFont);
        TextView tvPreview = dialog.findViewById(R.id.tvFontPreview);
        TextView tvFontLabel = dialog.findViewById(R.id.tvFontLabel);

        int currentSize = preferenceManager.getFontSize(); // 0=Small,1=Normal,2=Large,3=XL
        seekBar.setMax(3);
        seekBar.setProgress(currentSize);
        updateFontPreview(tvPreview, tvFontLabel, currentSize);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFontPreview(tvPreview, tvFontLabel, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dialog.findViewById(R.id.btnFontApply).setOnClickListener(v -> {
            preferenceManager.setFontSize(seekBar.getProgress());
            dialog.dismiss();
            recreate();
        });

        dialog.findViewById(R.id.btnFontCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void updateFontPreview(TextView tvPreview, TextView tvLabel, int progress) {
        String[] labels = {getString(R.string.font_small), getString(R.string.font_normal), getString(R.string.font_large), getString(R.string.font_extra_large)};
        float[] sizes = {12f, 14f, 17f, 20f};
        tvLabel.setText(labels[progress]);
        tvPreview.setTextSize(sizes[progress]);
        tvPreview.setText(R.string.sample_message_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAfterCallToggle();
        updateChatBubblesToggle();
    }
}
